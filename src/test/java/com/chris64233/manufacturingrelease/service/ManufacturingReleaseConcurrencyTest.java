package com.chris64233.manufacturingrelease.service;

import static com.chris64233.manufacturingrelease.TestFixtures.conforming;
import static com.chris64233.manufacturingrelease.TestFixtures.declareRequest;
import static com.chris64233.manufacturingrelease.TestFixtures.grantBothApprovals;
import static com.chris64233.manufacturingrelease.TestFixtures.submitAllConforming;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.manufacturingrelease.domain.BatchStatus;
import com.chris64233.manufacturingrelease.web.ApiRequests;
import com.chris64233.manufacturingrelease.web.Views.EvidencePackageView;
import com.chris64233.manufacturingrelease.web.Views.ReleaseView;

/**
 * 并发安全测试：补录检验结果、撤回审批与放行并发执行时，
 * 不允许出现依据不完整的已放行批次。
 */
@SpringBootTest
class ManufacturingReleaseConcurrencyTest {

    @Autowired
    private ManufacturingReleaseService service;

    /**
     * 放行事务进行中，另一个事务尝试为同一项目补录新版本结果。
     * 二者在批次行锁上串行：
     * <ul>
     *   <li>放行先拿到锁 → 补录在放行后执行，此时批次已冻结，补录失败，批次依据完整；</li>
     *   <li>补录先拿到锁 → 放行在补录后执行，锁定新版本，依据同样完整。</li>
     * </ul>
     */
    @Test
    void concurrentResultBackfillCannotProduceReleaseWithStaleOrMissingBasis() throws Exception {
        for (int iteration = 0; iteration < 5; iteration++) {
            final int iter = iteration;
            String batchNo = "B-CX-RES-" + iter;
            service.declareBatch(declareRequest(batchNo, "I1", "I2"));
            submitAllConforming(service, batchNo, "I1", "I2");
            grantBothApprovals(service, batchNo);

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            AtomicReference<Throwable> releaseError = new AtomicReference<>();
            AtomicReference<Throwable> backfillError = new AtomicReference<>();
            try {
                Future<?> releaseFuture = pool.submit(() -> {
                    await(start);
                    try {
                        service.release(batchNo,
                                new ApiRequests.ReleaseRequest("REL-CX-RES-" + iter, "sys"));
                    } catch (Throwable t) {
                        releaseError.set(t);
                    }
                });
                Future<?> backfillFuture = pool.submit(() -> {
                    await(start);
                    try {
                        // 补录一个新版本（模拟放行前的延迟补录）
                        service.submitResult(batchNo, conforming("I1", "backfilled"));
                    } catch (Throwable t) {
                        backfillError.set(t);
                    }
                });

                start.countDown();
                releaseFuture.get(30, TimeUnit.SECONDS);
                backfillFuture.get(30, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            EvidencePackageView pkg = service.getEvidencePackage(batchNo);
            assertThat(pkg.batch().status()).isEqualTo(BatchStatus.RELEASED);

            // 放行必然成功；补录要么在放行前完成（放行锁定 v2），要么在放行后被冻结拒绝。
            // 无论哪种顺序，放行锁定的检验版本必须与该项目的“当时当前版本”一致且完整。
            ReleaseView release = pkg.release();
            assertThat(release).isNotNull();
            var lockedItem = release.evidences().stream()
                    .filter(e -> e.sourceKey().equals("I1"))
                    .findFirst().orElseThrow();
            int backfillApplied = backfillError.get() == null ? 1 : 0;
            assertThat(lockedItem.lockedVersion()).isEqualTo(1 + backfillApplied);
            if (backfillApplied == 0) {
                // 补录在放行后到达：必须被拒绝，且历史中仍只有 v1
                assertThat(backfillError.get()).hasMessageContaining("不可修改");
                assertThat(pkg.versionHistory().get(0).results()).hasSize(1);
            } else {
                // 补录在放行前完成：当前版本是 v2，放行快照锁定 v2；v1 保留为历史
                assertThat(pkg.versionHistory().get(0).results()).hasSize(2);
            }
            // 两个检验项目都在证据中，无遗漏
            assertThat(release.evidences().stream().filter(e -> e.evidenceType().name()
                    .equals("INSPECTION_RESULT")).count()).isEqualTo(2);
        }
    }

    /**
     * 放行与撤回审批并发：若撤回先于放行的最终校验生效，放行必须失败；
     * 若放行先完成，撤回必须因批次冻结而失败。已放行批次必有两个不同角色的有效审批快照。
     */
    @Test
    void concurrentApprovalWithdrawalCannotReleaseWithSingleApproval() throws Exception {
        for (int iteration = 0; iteration < 5; iteration++) {
            final int iter = iteration;
            String batchNo = "B-CX-APPR-" + iter;
            service.declareBatch(declareRequest(batchNo, "I1"));
            submitAllConforming(service, batchNo, "I1");
            grantBothApprovals(service, batchNo);

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            AtomicReference<Throwable> releaseError = new AtomicReference<>();
            AtomicReference<Throwable> withdrawError = new AtomicReference<>();
            try {
                Future<?> releaseFuture = pool.submit(() -> {
                    await(start);
                    try {
                        service.release(batchNo,
                                new ApiRequests.ReleaseRequest("REL-CX-APPR-" + iter, "sys"));
                    } catch (Throwable t) {
                        releaseError.set(t);
                    }
                });
                Future<?> withdrawFuture = pool.submit(() -> {
                    await(start);
                    try {
                        service.withdrawApproval(batchNo, new ApiRequests.WithdrawApprovalRequest(
                                com.chris64233.manufacturingrelease.domain.ApprovalRole.QUALITY_MANAGER,
                                "qa"));
                    } catch (Throwable t) {
                        withdrawError.set(t);
                    }
                });

                start.countDown();
                releaseFuture.get(30, TimeUnit.SECONDS);
                withdrawFuture.get(30, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            boolean released = service.getBatch(batchNo).status() == BatchStatus.RELEASED;
            if (released) {
                // 放行先完成：撤回必须被拒绝，证据中锁定两个角色审批
                assertThat(withdrawError.get()).isNotNull();
                ReleaseView release = service.getRelease("REL-CX-APPR-" + iter);
                assertThat(release.evidences().stream()
                        .filter(e -> e.evidenceType().name().equals("APPROVAL")).count())
                        .isEqualTo(2);
            } else {
                // 撤回先生效：放行必须失败且批次未放行
                assertThat(releaseError.get()).isNotNull();
                assertThat(releaseError.get()).hasMessageContaining("放行条件");
                assertThat(withdrawError.get()).isNull();
                // 再次补齐审批后可正常放行
                service.grantApproval(batchNo, new ApiRequests.GrantApprovalRequest(
                        com.chris64233.manufacturingrelease.domain.ApprovalRole.QUALITY_MANAGER,
                        "qa-new", null));
                service.release(batchNo,
                        new ApiRequests.ReleaseRequest("REL-CX-APPR2-" + iter, "sys"));
                assertThat(service.getBatch(batchNo).status()).isEqualTo(BatchStatus.RELEASED);
            }
        }
    }

    /**
     * 相同放行业务号并发放行：只能产生一条放行，两个线程返回同一记录（幂等）。
     */
    @Test
    void concurrentSameReleaseNoReleasesExactlyOnce() throws Exception {
        String batchNo = "B-CX-IDEM";
        service.declareBatch(declareRequest(batchNo, "I1"));
        submitAllConforming(service, batchNo, "I1");
        grantBothApprovals(service, batchNo);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            AtomicReference<Throwable> e1 = new AtomicReference<>();
            AtomicReference<Throwable> e2 = new AtomicReference<>();
            Future<?> f1 = pool.submit(() -> {
                await(start);
                try {
                    service.release(batchNo, new ApiRequests.ReleaseRequest("REL-CX-IDEM", "a"));
                } catch (Throwable t) {
                    e1.set(t);
                }
            });
            Future<?> f2 = pool.submit(() -> {
                await(start);
                try {
                    service.release(batchNo, new ApiRequests.ReleaseRequest("REL-CX-IDEM", "b"));
                } catch (Throwable t) {
                    e2.set(t);
                }
            });
            start.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);

            assertThat(e1.get()).isNull();
            assertThat(e2.get()).isNull();
            ReleaseView view = service.getRelease("REL-CX-IDEM");
            // 先获得批次锁的一方胜出，另一方重放同一记录；只会有一条放行
            assertThat(view.releasedBy()).isIn("a", "b");
            assertThat(view.batchStatus()).isEqualTo(BatchStatus.RELEASED);
            assertThat(service.getEvidencePackage(batchNo).release().releaseNo())
                    .isEqualTo("REL-CX-IDEM");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
