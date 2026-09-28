package com.chris64233.manufacturingrelease.service;

import static com.chris64233.manufacturingrelease.TestFixtures.conforming;
import static com.chris64233.manufacturingrelease.TestFixtures.declareRequest;
import static com.chris64233.manufacturingrelease.TestFixtures.grantBothApprovals;
import static com.chris64233.manufacturingrelease.TestFixtures.nonconforming;
import static com.chris64233.manufacturingrelease.TestFixtures.prepareReleasableBatch;
import static com.chris64233.manufacturingrelease.TestFixtures.submitAllConforming;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.manufacturingrelease.domain.ApprovalRole;
import com.chris64233.manufacturingrelease.domain.BatchStatus;
import com.chris64233.manufacturingrelease.domain.Conformity;
import com.chris64233.manufacturingrelease.domain.DeviationStatus;
import com.chris64233.manufacturingrelease.domain.Disposition;
import com.chris64233.manufacturingrelease.domain.EvidenceType;
import com.chris64233.manufacturingrelease.web.ApiRequests;
import com.chris64233.manufacturingrelease.web.Views.ApprovalView;
import com.chris64233.manufacturingrelease.web.Views.ConclusionView;
import com.chris64233.manufacturingrelease.web.Views.EvidencePackageView;
import com.chris64233.manufacturingrelease.web.Views.EvidenceView;
import com.chris64233.manufacturingrelease.web.Views.InspectionResultView;
import com.chris64233.manufacturingrelease.web.Views.ItemHistoryView;
import com.chris64233.manufacturingrelease.web.Views.ReleaseView;

@SpringBootTest
class ManufacturingReleaseRulesTest {

    @Autowired
    private ManufacturingReleaseService service;

    @Test
    void declaresBatchWithProductQuantityAndRequiredItems() {
        var batch = service.declareBatch(declareRequest("B-DECL", "I1", "I2"));

        assertThat(batch.batchNo()).isEqualTo("B-DECL");
        assertThat(batch.productCode()).isEqualTo("PROD-1");
        assertThat(batch.quantity()).isEqualByComparingTo("100.0000");
        assertThat(batch.status()).isEqualTo(BatchStatus.PENDING_RELEASE);

        var conclusion = service.getConclusion("B-DECL");
        assertThat(conclusion.items()).extracting("itemCode").containsExactly("I1", "I2");
        assertThat(conclusion.conclusion()).isEqualTo("BLOCKED");
        assertThat(conclusion.blockingReasons()).anyMatch(r -> r.contains("I1"));
    }

    @Test
    void rejectsDuplicateBatchNo() {
        service.declareBatch(declareRequest("B-DUP", "I1"));
        assertThatThrownBy(() -> service.declareBatch(declareRequest("B-DUP", "I1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("批次号已存在");
    }

    // ---- 检验结果版本 -------------------------------------------------

    @Test
    void submittingNewResultSupersedesOldVersionWhichIsRetainedButNotCurrent() {
        service.declareBatch(declareRequest("B-VER", "I1"));

        var v1 = service.submitResult("B-VER", conforming("I1", "first"));
        var v2 = service.submitResult("B-VER", conforming("I1", "second"));

        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.current()).isTrue();

        List<ItemHistoryView> history = service.getVersionHistory("B-VER");
        List<InspectionResultView> results = history.get(0).results();
        assertThat(results).hasSize(2);
        assertThat(results).extracting(InspectionResultView::version).containsExactly(1, 2);
        assertThat(results).extracting(InspectionResultView::current).containsExactly(false, true);
        // 历史版本保留完整内容
        assertThat(results.get(0).resultValue()).isEqualTo("first");
    }

    @Test
    void nonconformingResultMustReferenceDeviation() {
        service.declareBatch(declareRequest("B-NG-REQ", "I1"));
        var request = new ApiRequests.SubmitResultRequest("I1", "NG", false, null, "tester");
        assertThatThrownBy(() -> service.submitResult("B-NG-REQ", request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("必须关联偏差单");
    }

    @Test
    void conformingResultCannotReferenceDeviation() {
        service.declareBatch(declareRequest("B-CF-DEV", "I1"));
        service.registerDeviation("B-CF-DEV",
                new ApiRequests.RegisterDeviationRequest("D-CF", "x"));
        assertThatThrownBy(() -> service.submitResult("B-CF-DEV",
                new ApiRequests.SubmitResultRequest("I1", "OK", true, "D-CF", "tester")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("合格检验结果不允许关联偏差单");
    }

    // ---- 偏差处置 -----------------------------------------------------

    @Test
    void openOrScrapDeviationBlocksReleaseButConditionalAcceptanceAllows() {
        service.declareBatch(declareRequest("B-DEV", "I1"));
        service.registerDeviation("B-DEV", new ApiRequests.RegisterDeviationRequest("D-1", "外观不良"));
        service.submitResult("B-DEV", nonconforming("I1", "D-1"));
        grantBothApprovals(service, "B-DEV");

        // 偏差未处置：阻塞
        assertBlocked("B-DEV", "D-1");

        // 报废：仍阻塞（不允许放行）
        service.decideDeviation("B-DEV", "D-1", new ApiRequests.DecideDeviationRequest(
                Disposition.SCRAP, "判废", "qa"));
        assertBlocked("B-DEV", "不允许放行");
        assertThat(service.getConclusion("B-DEV").deviations().get(0).status())
                .isEqualTo(DeviationStatus.DECIDED);

        // 已终态的偏差不能再次处置
        assertThatThrownBy(() -> service.decideDeviation("B-DEV", "D-1",
                new ApiRequests.DecideDeviationRequest(Disposition.CONDITIONAL_ACCEPTANCE, "改判", "qa")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("终态");
    }

    @Test
    void reworkAndConditionalAcceptanceAreReleasePermitting() {
        // 返工
        service.declareBatch(declareRequest("B-REWORK", "I1"));
        service.registerDeviation("B-REWORK", new ApiRequests.RegisterDeviationRequest("D-R", "返工"));
        service.submitResult("B-REWORK", nonconforming("I1", "D-R"));
        service.decideDeviation("B-REWORK", "D-R", new ApiRequests.DecideDeviationRequest(
                Disposition.REWORK, "返工后复检", "qa"));
        grantBothApprovals(service, "B-REWORK");
        assertThat(service.getConclusion("B-REWORK").conclusion()).isEqualTo("RELEASABLE");

        // 有条件接受
        service.declareBatch(declareRequest("B-COND", "I1"));
        service.registerDeviation("B-COND", new ApiRequests.RegisterDeviationRequest("D-C", "让步接收"));
        service.submitResult("B-COND", nonconforming("I1", "D-C"));
        service.decideDeviation("B-COND", "D-C", new ApiRequests.DecideDeviationRequest(
                Disposition.CONDITIONAL_ACCEPTANCE, "限本批使用", "qa"));
        grantBothApprovals(service, "B-COND");
        assertThat(service.getConclusion("B-COND").conclusion()).isEqualTo("RELEASABLE");
    }

    @Test
    void cannotDecideDeviationOfAnotherBatch() {
        service.declareBatch(declareRequest("B-A", "I1"));
        service.declareBatch(declareRequest("B-B", "I1"));
        service.registerDeviation("B-A", new ApiRequests.RegisterDeviationRequest("D-A", "x"));
        assertThatThrownBy(() -> service.decideDeviation("B-B", "D-A",
                new ApiRequests.DecideDeviationRequest(Disposition.REWORK, "x", "qa")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不属于批次");
    }

    // ---- 审批 ---------------------------------------------------------

    @Test
    void releaseRequiresTwoDistinctActiveRoles() {
        service.declareBatch(declareRequest("B-APPR", "I1"));
        submitAllConforming(service, "B-APPR", "I1");

        // 无审批
        assertBlocked("B-APPR", "有效审批");

        // 仅一个角色
        service.grantApproval("B-APPR", new ApiRequests.GrantApprovalRequest(
                ApprovalRole.QUALITY_MANAGER, "qa", null));
        assertBlocked("B-APPR", "有效审批");

        // 同一角色不能重复授权
        assertThatThrownBy(() -> service.grantApproval("B-APPR", new ApiRequests.GrantApprovalRequest(
                ApprovalRole.QUALITY_MANAGER, "qa2", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已有有效审批");

        // 补齐第二角色后可放行
        service.grantApproval("B-APPR", new ApiRequests.GrantApprovalRequest(
                ApprovalRole.PRODUCTION_MANAGER, "prod", null));
        assertThat(service.getConclusion("B-APPR").conclusion()).isEqualTo("RELEASABLE");
    }

    @Test
    void withdrawalMakesApprovalInactiveAndHistoryIsKept() {
        prepareReleasableBatch(service, "B-WD", "I1");
        ApprovalView withdrawn = service.withdrawApproval("B-WD",
                new ApiRequests.WithdrawApprovalRequest(ApprovalRole.QUALITY_MANAGER, "qa"));
        assertThat(withdrawn.active()).isFalse();
        assertThat(withdrawn.withdrawnAt()).isNotNull();
        assertBlocked("B-WD", "有效审批");

        // 重新授权后可放行；撤回记录仍保留
        service.grantApproval("B-WD", new ApiRequests.GrantApprovalRequest(
                ApprovalRole.QUALITY_MANAGER, "qa-new", "重新审批"));
        ReleaseView release = service.release("B-WD",
                new ApiRequests.ReleaseRequest("REL-WD", "system"));
        assertThat(release.batchStatus()).isEqualTo(BatchStatus.RELEASED);
    }

    @Test
    void withdrawWithoutActiveApprovalFails() {
        service.declareBatch(declareRequest("B-WD2", "I1"));
        assertThatThrownBy(() -> service.withdrawApproval("B-WD2",
                new ApiRequests.WithdrawApprovalRequest(ApprovalRole.QUALITY_MANAGER, "qa")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("没有可撤回");
    }

    // ---- 放行与证据快照 ------------------------------------------------

    @Test
    void happyPathReleaseLocksEvidenceSnapshot() {
        prepareReleasableBatch(service, "B-OK", "I1", "I2");

        ReleaseView release = service.release("B-OK",
                new ApiRequests.ReleaseRequest("REL-OK", "system"));

        assertThat(release.releaseNo()).isEqualTo("REL-OK");
        assertThat(release.batchStatus()).isEqualTo(BatchStatus.RELEASED);
        assertThat(release.revoked()).isFalse();
        assertThat(release.quantity()).isEqualByComparingTo("100.0000");

        List<EvidenceView> evidences = release.evidences();
        assertThat(evidences).hasSize(4); // 2 检验结果 + 2 审批
        assertThat(evidences).extracting(EvidenceView::evidenceType)
                .containsExactlyInAnyOrder(
                        EvidenceType.INSPECTION_RESULT, EvidenceType.INSPECTION_RESULT,
                        EvidenceType.APPROVAL, EvidenceType.APPROVAL);
        assertThat(evidences.stream().filter(e -> e.evidenceType() == EvidenceType.INSPECTION_RESULT))
                .extracting(EvidenceView::sourceKey).containsExactlyInAnyOrder("I1", "I2");
        assertThat(evidences.stream().filter(e -> e.evidenceType() == EvidenceType.INSPECTION_RESULT))
                .allSatisfy(e -> assertThat(e.lockedVersion()).isEqualTo(1));
    }

    @Test
    void releaseIsIdempotentByReleaseNo() {
        prepareReleasableBatch(service, "B-IDEM", "I1");

        ReleaseView first = service.release("B-IDEM",
                new ApiRequests.ReleaseRequest("REL-IDEM", "user-a"));
        ReleaseView second = service.release("B-IDEM",
                new ApiRequests.ReleaseRequest("REL-IDEM", "user-b"));

        assertThat(second.releasedAt().toEpochMilli()).isEqualTo(first.releasedAt().toEpochMilli());
        assertThat(second.releasedBy()).isEqualTo("user-a");
        assertThat(second.evidences()).hasSize(first.evidences().size());
        assertThat(second.evidences()).extracting(EvidenceView::sourceId)
                .containsExactlyElementsOf(first.evidences().stream()
                        .map(EvidenceView::sourceId).toList());
    }

    @Test
    void releaseNoCannotBeReusedForAnotherBatch() {
        prepareReleasableBatch(service, "B-U1", "I1");
        prepareReleasableBatch(service, "B-U2", "I1");
        service.release("B-U1", new ApiRequests.ReleaseRequest("REL-UNIQ", "a"));
        assertThatThrownBy(() -> service.release("B-U2",
                new ApiRequests.ReleaseRequest("REL-UNIQ", "b")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已用于其他批次");
    }

    @Test
    void cannotReleaseTwiceWithDifferentReleaseNos() {
        prepareReleasableBatch(service, "B-TWICE", "I1");
        service.release("B-TWICE", new ApiRequests.ReleaseRequest("REL-1", "a"));
        assertThatThrownBy(() -> service.release("B-TWICE",
                new ApiRequests.ReleaseRequest("REL-2", "b")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不允许放行");
    }

    @Test
    void supersededHistoryVersionCannotServeAsReleaseBasis() {
        // v1 合格；v2 改为不合格并关联一张未处置的偏差单。放行必须以“当前版本”为准，
        // 历史 v1 的合格结论不能用于放行。
        service.declareBatch(declareRequest("B-HIST", "I1"));
        service.submitResult("B-HIST", conforming("I1", "v1-ok"));
        service.registerDeviation("B-HIST", new ApiRequests.RegisterDeviationRequest("D-H", "v2 不良"));
        service.submitResult("B-HIST", nonconforming("I1", "D-H"));
        grantBothApprovals(service, "B-HIST");

        assertBlocked("B-HIST", "D-H");

        // 偏差作出允许放行的终态处理后，当前版本（v2 + 偏差）才构成完整依据
        service.decideDeviation("B-HIST", "D-H", new ApiRequests.DecideDeviationRequest(
                Disposition.CONDITIONAL_ACCEPTANCE, "让步", "qa"));
        ReleaseView release = service.release("B-HIST",
                new ApiRequests.ReleaseRequest("REL-HIST", "a"));
        assertThat(release.evidences().stream()
                .filter(e -> e.evidenceType() == EvidenceType.INSPECTION_RESULT)
                .findFirst().orElseThrow().lockedVersion()).isEqualTo(2);
    }

    // ---- 放行后冻结 ----------------------------------------------------

    @Test
    void releasedBatchQuantityAndBasisAreImmutable() {
        prepareReleasableBatch(service, "B-FROZEN", "I1");
        service.release("B-FROZEN", new ApiRequests.ReleaseRequest("REL-F", "a"));

        assertThatThrownBy(() -> service.submitResult("B-FROZEN", conforming("I1", "later")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不可修改");
        assertThatThrownBy(() -> service.registerDeviation("B-FROZEN",
                new ApiRequests.RegisterDeviationRequest("D-LATE", "x")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.grantApproval("B-FROZEN", new ApiRequests.GrantApprovalRequest(
                ApprovalRole.QUALITY_MANAGER, "qa", null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.withdrawApproval("B-FROZEN",
                new ApiRequests.WithdrawApprovalRequest(ApprovalRole.QUALITY_MANAGER, "qa")))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- 撤销放行 ------------------------------------------------------

    @Test
    void revokeRegistersEventLocksBatchAndBlocksOutbound() {
        prepareReleasableBatch(service, "B-REV", "I1");
        service.release("B-REV", new ApiRequests.ReleaseRequest("REL-R", "a"));

        // 已放行：出库闸门通过
        service.assertOutboundAllowed("B-REV");

        ReleaseView revoked = service.revokeRelease("B-REV",
                new ApiRequests.RevokeReleaseRequest("发现检验错误", "qa-director"));

        assertThat(revoked.revoked()).isTrue();
        assertThat(revoked.batchStatus()).isEqualTo(BatchStatus.RELEASE_REVOKED);
        assertThat(revoked.latestRevocation().reason()).isEqualTo("发现检验错误");

        // 撤销后阻止继续流转
        assertThatThrownBy(() -> service.assertOutboundAllowed("B-REV"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("阻止继续流转");

        // 原放行与证据仍可查询；批次数据仍不可修改
        ReleaseView fetched = service.getRelease("REL-R");
        assertThat(fetched.evidences()).isNotEmpty();
        assertThatThrownBy(() -> service.submitResult("B-REV", conforming("I1")))
                .isInstanceOf(BusinessRuleException.class);

        // 不能重复撤销
        assertThatThrownBy(() -> service.revokeRelease("B-REV",
                new ApiRequests.RevokeReleaseRequest("again", "qa")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已撤销");
    }

    @Test
    void cannotRevokeUnreleasedBatch() {
        service.declareBatch(declareRequest("B-NR", "I1"));
        assertThatThrownBy(() -> service.revokeRelease("B-NR",
                new ApiRequests.RevokeReleaseRequest("x", "qa")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("尚未放行");
    }

    // ---- 证据包 / 结论 / 历史 ------------------------------------------

    @Test
    void evidencePackageContainsConclusionAndFullVersionHistory() {
        service.declareBatch(declareRequest("B-PKG", "I1"));
        service.registerDeviation("B-PKG", new ApiRequests.RegisterDeviationRequest("D-P", "x"));
        service.submitResult("B-PKG", nonconforming("I1", "D-P"));
        service.submitResult("B-PKG", conforming("I1", "reinspect-ok")); // 偏差经返工后复检合格
        service.decideDeviation("B-PKG", "D-P",
                new ApiRequests.DecideDeviationRequest(Disposition.REWORK, "返工", "qa"));
        grantBothApprovals(service, "B-PKG");
        service.release("B-PKG", new ApiRequests.ReleaseRequest("REL-P", "a"));

        EvidencePackageView pkg = service.getEvidencePackage("B-PKG");

        // 当前结论：已放行
        assertThat(pkg.currentConclusion().conclusion()).isEqualTo("RELEASED");
        // 完整版本历史（含被替代的 v1 不合格结果）
        assertThat(pkg.versionHistory()).hasSize(1);
        List<InspectionResultView> results = pkg.versionHistory().get(0).results();
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).satisfies(r -> {
            assertThat(r.version()).isEqualTo(1);
            assertThat(r.current()).isFalse();
            assertThat(r.conformity()).isEqualTo(Conformity.NONCONFORMING);
            assertThat(r.deviationNo()).isEqualTo("D-P");
        });
        assertThat(results.get(1)).satisfies(r -> {
            assertThat(r.version()).isEqualTo(2);
            assertThat(r.current()).isTrue();
            assertThat(r.conformity()).isEqualTo(Conformity.CONFORMING);
        });
        // 放行证据锁定的是当前 v2，而非历史 v1
        assertThat(pkg.release().evidences().stream()
                .filter(e -> e.evidenceType() == EvidenceType.INSPECTION_RESULT)
                .findFirst().orElseThrow().lockedVersion()).isEqualTo(2);
        assertThat(pkg.release().evidences().stream()
                .filter(e -> e.evidenceType() == EvidenceType.DEVIATION)).hasSize(1);
    }

    @Test
    void evidencePackageBeforeReleaseHasNoReleaseButShowsBlockedConclusion() {
        service.declareBatch(declareRequest("B-BEFORE", "I1"));
        EvidencePackageView pkg = service.getEvidencePackage("B-BEFORE");
        assertThat(pkg.release()).isNull();
        assertThat(pkg.currentConclusion().conclusion()).isEqualTo("BLOCKED");
    }

    // ---- 辅助 ----------------------------------------------------------

    private void assertBlocked(String batchNo, String reasonFragment) {
        ConclusionView conclusion = service.getConclusion(batchNo);
        assertThat(conclusion.conclusion()).isEqualTo("BLOCKED");
        assertThat(conclusion.blockingReasons())
                .anyMatch(r -> r.contains(reasonFragment));
        assertThatThrownBy(() -> service.release(batchNo,
                new ApiRequests.ReleaseRequest("REL-SHOULD-FAIL-" + batchNo, "a")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(reasonFragment);
    }
}
