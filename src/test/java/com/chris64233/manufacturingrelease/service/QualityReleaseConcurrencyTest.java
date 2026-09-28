package com.chris64233.manufacturingrelease.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.manufacturingrelease.domain.Batch;
import com.chris64233.manufacturingrelease.domain.BatchStatus;
import com.chris64233.manufacturingrelease.domain.InspectionItem;
import com.chris64233.manufacturingrelease.domain.InspectionResultVersion;
import com.chris64233.manufacturingrelease.domain.Release;
import com.chris64233.manufacturingrelease.domain.ReleaseEvidenceItem;
import com.chris64233.manufacturingrelease.domain.ReleaseRepository;
import com.chris64233.manufacturingrelease.service.QualityReleaseService.ItemDeclaration;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 并发安全测试：在批次行悲观锁的串行化下，放行与"撤回审批 / 补录结果 / 重复放行"并发时，
 * 绝不产生依据不完整的已放行批次。
 *
 * <p>每个场景多轮运行，用 {@link CyclicBarrier} 让两个事务在同一刻发起，
 * 不论谁先拿到批次行锁，结果都必须满足不变量。
 */
@SpringBootTest
class QualityReleaseConcurrencyTest {

    private static final String QA = "QA_MANAGER";
    private static final String PROD = "PRODUCTION_MANAGER";

    @Autowired
    private QualityReleaseService service;

    @Autowired
    private ReleaseRepository releaseRepository;

    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private void readyForRelease(String batchNo) {
        service.declareBatch(batchNo, "P-1", "产品", new BigDecimal("100"),
                List.of(new ItemDeclaration("I1", "含量", null),
                        new ItemDeclaration("I2", "无菌", null)),
                "setup");
        service.submitResult(batchNo, "I1", true, "100%", null, "qc");
        service.submitResult(batchNo, "I2", true, "合格", null, "qc");
        service.grantApproval(batchNo, QA, "alice", null);
        service.grantApproval(batchNo, PROD, "bob", null);
    }

    /** 撤回审批 与 放行 同时发生：只有两种合法结局，绝不允许"撤回生效但批次仍放行"。 */
    @Test
    void releaseConcurrentWithWithdrawalNeverReleasesOnSingleApproval() throws Exception {
        int rounds = 20;
        for (int i = 0; i < rounds; i++) {
            String batchNo = "B-CW-" + i;
            String releaseNo = "R-CW-" + i;
            readyForRelease(batchNo);
            CyclicBarrier barrier = new CyclicBarrier(2);

            Future<?> release = pool.submit(() -> {
                barrier.await();
                service.release(releaseNo, batchNo, "qa");
                return null;
            });
            Future<?> withdraw = pool.submit(() -> {
                barrier.await();
                service.withdrawApproval(batchNo, QA, "alice");
                return null;
            });

            Throwable releaseError = failureOf(release);
            Throwable withdrawError = failureOf(withdraw);

            Batch batch = service.getBatch(batchNo);
            boolean qaActive = batch.getApprovals().stream()
                    .filter(a -> a.getRoleCode().equals(QA))
                    .reduce((first, second) -> second).orElseThrow().isWithdrawn() == false;

            if (batch.getStatus() == BatchStatus.RELEASED) {
                // 放行先提交：撤回必须被拒，QA 审批仍有效，证据完整锁定
                assertThat(withdrawError).as("轮次 " + i + " 放行先生效，撤回必须失败")
                        .isInstanceOf(BusinessRuleException.class);
                assertThat(releaseError).isNull();
                assertThat(qaActive).isTrue();
                Release locked = service.evidencePackage(releaseNo);
                assertThat(locked.getEvidenceItems()).hasSize(2);
            } else {
                // 撤回先提交：放行必须因审批不足失败，且不存在放行记录
                assertThat(batch.getStatus()).isEqualTo(BatchStatus.CREATED);
                assertThat(releaseError).as("轮次 " + i + " 撤回先生效，放行必须失败")
                        .isInstanceOf(BusinessRuleException.class);
                assertThat(withdrawError).isNull();
                assertThat(qaActive).isFalse();
                assertThat(releaseRepository.findByBatchId(batch.getId())).isEmpty();
            }
        }
    }

    /** 补录新版本结果 与 放行 同时发生：放行快照必须锁定其校验时刻的当前版本，不能引用被替代的版本。 */
    @Test
    void releaseConcurrentWithLateResultAlwaysLocksTheVersionItValidated() throws Exception {
        int rounds = 20;
        for (int i = 0; i < rounds; i++) {
            String batchNo = "B-CR-" + i;
            String releaseNo = "R-CR-" + i;
            readyForRelease(batchNo);
            CyclicBarrier barrier = new CyclicBarrier(2);

            Future<?> release = pool.submit(() -> {
                barrier.await();
                service.release(releaseNo, batchNo, "qa");
                return null;
            });
            Future<?> lateResult = pool.submit(() -> {
                barrier.await();
                // 补录一版合格结果（无需偏差），替代第 1 版
                service.submitResult(batchNo, "I1", true, "101%-复核", null, "qc2");
                return null;
            });

            Throwable releaseError = failureOf(release);
            Throwable lateError = failureOf(lateResult);
            assertThat(releaseError).isNull();

            Batch batch = service.getBatch(batchNo);
            assertThat(batch.getStatus()).isEqualTo(BatchStatus.RELEASED);
            Release locked = service.evidencePackage(releaseNo);

            ReleaseEvidenceItem i1Snapshot = locked.getEvidenceItems().stream()
                    .filter(e -> "RESULT".equals(e.getEvidenceType()) && "I1".equals(e.getItemCode()))
                    .findFirst().orElseThrow();
            InspectionItem item = batch.findItem("I1").orElseThrow();

            if (lateError == null) {
                // 补录先提交：放行必须锁定第 2 版
                assertThat(item.getResults()).hasSize(2);
                assertThat(i1Snapshot.getResultVersionNo()).isEqualTo(2);
                InspectionResultVersion lockedRow = item.getResults().stream()
                        .filter(r -> r.getVersionNo() == 2).findFirst().orElseThrow();
                assertThat(lockedRow.isSuperseded()).isFalse();
            } else {
                // 放行先提交：补录必须因批次不可变被拒，锁定的是第 1 版
                assertThat(lateError).isInstanceOf(BusinessRuleException.class);
                assertThat(item.getResults()).hasSize(1);
                assertThat(i1Snapshot.getResultVersionNo()).isEqualTo(1);
            }
        }
    }

    /** 同一放行业务号并发重试：最终只有一张放行单；顺序重试幂等返回同一记录。 */
    @Test
    void concurrentSameReleaseNoLeavesSingleReleaseAndStaysIdempotent() throws Exception {
        String batchNo = "B-CI";
        String releaseNo = "R-CI";
        readyForRelease(batchNo);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Future<?> first = pool.submit(() -> {
            barrier.await();
            service.release(releaseNo, batchNo, "qa");
            return null;
        });
        Future<?> second = pool.submit(() -> {
            barrier.await();
            service.release(releaseNo, batchNo, "qa");
            return null;
        });

        // 至少一方成功；唯一约束保证不可能产生两张放行单
        int successes = (failureOf(first) == null ? 1 : 0) + (failureOf(second) == null ? 1 : 0);
        assertThat(successes).isGreaterThanOrEqualTo(1);

        Batch batch = service.getBatch(batchNo);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.RELEASED);
        Release only = releaseRepository.findByBatchId(batch.getId()).orElseThrow();
        assertThat(only.getReleaseNo()).isEqualTo(releaseNo);

        // 失败方之后用同一业务号重试：幂等返回同一张放行单
        Release retry = service.release(releaseNo, batchNo, "retry");
        assertThat(retry.getId()).isEqualTo(only.getId());
    }

    /** 两个不同放行业务号并发放行同一批次：只可能有一个成功。 */
    @Test
    void concurrentDifferentReleaseNosOnlyOneWins() throws Exception {
        String batchNo = "B-CN";
        readyForRelease(batchNo);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Future<?> first = pool.submit(() -> {
            barrier.await();
            service.release("R-CN-1", batchNo, "qa");
            return null;
        });
        Future<?> second = pool.submit(() -> {
            barrier.await();
            service.release("R-CN-2", batchNo, "qa");
            return null;
        });

        Throwable e1 = failureOf(first);
        Throwable e2 = failureOf(second);
        assertThat(e1 == null || e2 == null).isTrue();
        assertThat(e1 == null && e2 == null).isFalse();
        assertThat(releaseRepository.findByBatchId(service.getBatch(batchNo).getId())).isPresent();
    }

    private static Throwable failureOf(Future<?> future) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            return cause;
        }
    }
}
