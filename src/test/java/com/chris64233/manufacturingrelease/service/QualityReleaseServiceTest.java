package com.chris64233.manufacturingrelease.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.manufacturingrelease.domain.Batch;
import com.chris64233.manufacturingrelease.domain.BatchStatus;
import com.chris64233.manufacturingrelease.domain.DeviationStatus;
import com.chris64233.manufacturingrelease.domain.DispositionDecision;
import com.chris64233.manufacturingrelease.domain.InspectionItem;
import com.chris64233.manufacturingrelease.domain.Release;
import com.chris64233.manufacturingrelease.domain.ReleaseEvidenceItem;
import com.chris64233.manufacturingrelease.domain.ReleaseRevocation;
import com.chris64233.manufacturingrelease.service.QualityReleaseService.CurrentConclusion;
import com.chris64233.manufacturingrelease.service.QualityReleaseService.ItemDeclaration;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 质量放行核心业务规则测试。
 */
@SpringBootTest
class QualityReleaseServiceTest {

    private static final String QA = "QA_MANAGER";
    private static final String PROD = "PRODUCTION_MANAGER";

    @Autowired
    private QualityReleaseService service;

    @Autowired
    private com.chris64233.manufacturingrelease.domain.BatchRepository batchRepository;

    private Batch declare(String batchNo) {
        return service.declareBatch(batchNo, "P-100", "注射液", new BigDecimal("1000"),
                List.of(new ItemDeclaration("I1", "含量", "95~105%"),
                        new ItemDeclaration("I2", "无菌", "无菌")),
                "tester");
    }

    private void allConforming(String batchNo) {
        service.submitResult(batchNo, "I1", true, "100%", null, "qc1");
        service.submitResult(batchNo, "I2", true, "合格", null, "qc1");
    }

    private void twoApprovals(String batchNo) {
        service.grantApproval(batchNo, QA, "alice", "ok");
        service.grantApproval(batchNo, PROD, "bob", "ok");
    }

    private Release fullyRelease(String batchNo, String releaseNo) {
        allConforming(batchNo);
        twoApprovals(batchNo);
        return service.release(releaseNo, batchNo, "qa-lead");
    }

    // ----- 批次声明 -----

    @Test
    void declareBatchRequiresAtLeastOneItem() {
        assertThatThrownBy(() -> service.declareBatch("B-X", "P", null, BigDecimal.ONE, List.of(), "t"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void duplicateBatchNoRejected() {
        declare("B-DUP");
        assertThatThrownBy(() -> declare("B-DUP")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void duplicateItemCodeRejected() {
        List<ItemDeclaration> items = List.of(
                new ItemDeclaration("SAME", "a", null),
                new ItemDeclaration("SAME", "b", null));
        assertThatThrownBy(() -> service.declareBatch("B-DUP2", "P", null, BigDecimal.ONE, items, "t"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ----- 版本化结果 -----

    @Test
    void submittingNewResultSupersedesPreviousButKeepsHistory() {
        declare("B-VER");
        service.submitResult("B-VER", "I1", true, "99%", null, "qc");
        service.openDeviation("B-VER", "D-VER", "复测偏低", "qc");
        service.submitResult("B-VER", "I1", false, "90%", "D-VER", "qc");
        Batch batch = service.getBatch("B-VER");
        InspectionItem item = batch.findItem("I1").orElseThrow();
        assertThat(item.getResults()).hasSize(2);
        assertThat(item.getResults().get(0).isSuperseded()).isTrue();
        assertThat(item.getResults().get(1).isSuperseded()).isFalse();
        assertThat(item.currentResult().orElseThrow().getVersionNo()).isEqualTo(2);
        // 被替代的历史版本仍可追溯
        assertThat(item.getResults().get(0).getValueText()).isEqualTo("99%");
    }

    @Test
    void nonConformingResultMustReferenceDeviation() {
        declare("B-NC");
        assertThatThrownBy(() -> service.submitResult("B-NC", "I1", false, "x", null, "qc"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("偏差");
    }

    @Test
    void deviationFromOtherBatchCannotBeLinked() {
        declare("B-A");
        declare("B-B");
        service.openDeviation("B-A", "D-A", "不合格", "qc");
        assertThatThrownBy(() -> service.submitResult("B-B", "I1", false, "x", "D-A", "qc"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void closedDeviationCannotBackNewNonConformingResult() {
        declare("B-CLOSED");
        service.openDeviation("B-CLOSED", "D-C", "不合格", "qc");
        service.disposeDeviation("D-C", DispositionDecision.CONDITIONAL_ACCEPT, "接受", null, "qa");
        assertThatThrownBy(() -> service.submitResult("B-CLOSED", "I1", false, "x", "D-C", "qc"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已关闭");
    }

    // ----- 偏差处置 -----

    @Test
    void scrapDeviationBlocksReleaseForever() {
        declare("B-SCRAP");
        service.openDeviation("B-SCRAP", "D-S", "严重不合格", "qc");
        service.submitResult("B-SCRAP", "I1", false, "80%", "D-S", "qc");
        service.disposeDeviation("D-S", DispositionDecision.SCRAP, "报废", null, "qa");
        service.submitResult("B-SCRAP", "I1", true, "返工无效", null, "qc");
        service.submitResult("B-SCRAP", "I2", true, "合格", null, "qc");
        twoApprovals("B-SCRAP");
        assertThatThrownBy(() -> service.release("R-S", "B-SCRAP", "qa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("报废");
    }

    @Test
    void reworkAllowsReleaseOnlyAfterReinspectionConforming() {
        declare("B-REWORK");
        service.openDeviation("B-REWORK", "D-R", "含量偏低", "qc");
        service.submitResult("B-REWORK", "I1", false, "90%", "D-R", "qc");
        service.submitResult("B-REWORK", "I2", true, "合格", null, "qc");
        service.disposeDeviation("D-R", DispositionDecision.REWORK, "重新配料", "I1", "qa");
        assertThat(service.getBatch("B-REWORK").getDeviations().get(0).getStatus())
                .isEqualTo(DeviationStatus.IN_DISPOSITION);

        // 返工未复验：偏差处置中，不能放行
        twoApprovals("B-REWORK");
        assertThatThrownBy(() -> service.release("R-R1", "B-REWORK", "qa"))
                .isInstanceOf(BusinessRuleException.class);

        // 复验仍不合格（关联同一开放偏差）：仍不能放行
        service.submitResult("B-REWORK", "I1", false, "91%", "D-R", "qc");
        assertThatThrownBy(() -> service.release("R-R2", "B-REWORK", "qa"))
                .isInstanceOf(BusinessRuleException.class);

        // 复验合格：偏差自动关闭，可以放行
        service.submitResult("B-REWORK", "I1", true, "100%", null, "qc");
        Release release = service.release("R-R3", "B-REWORK", "qa");
        assertThat(release.getEvidenceItems()).hasSize(3); // 2 个结果版本 + 1 个偏差版本
        assertThat(service.getBatch("B-REWORK").getStatus()).isEqualTo(BatchStatus.RELEASED);
    }

    @Test
    void reworkRequiresReworkItem() {
        declare("B-RW");
        service.openDeviation("B-RW", "D-RW", "x", "qc");
        assertThatThrownBy(() -> service.disposeDeviation("D-RW", DispositionDecision.REWORK, "x", null, "qa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("待复验");
    }

    @Test
    void deviationCannotBeDisposedTwice() {
        declare("B-2X");
        service.openDeviation("B-2X", "D-2X", "x", "qc");
        service.disposeDeviation("D-2X", DispositionDecision.CONDITIONAL_ACCEPT, "a", null, "qa");
        assertThatThrownBy(() -> service.disposeDeviation(
                "D-2X", DispositionDecision.CONDITIONAL_ACCEPT, "b", null, "qa"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void openDeviationAloneBlocksRelease() {
        declare("B-OPEN");
        allConforming("B-OPEN");
        service.openDeviation("B-OPEN", "D-O", "观察项", "qc");
        twoApprovals("B-OPEN");
        assertThatThrownBy(() -> service.release("R-O", "B-OPEN", "qa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("未终态关闭");
    }

    // ----- 审批 -----

    @Test
    void releaseRequiresTwoDistinctActiveRoles() {
        declare("B-APPR");
        allConforming("B-APPR");

        service.grantApproval("B-APPR", QA, "alice", null);
        assertThatThrownBy(() -> service.release("R-A1", "B-APPR", "qa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("两个不同角色");

        // 同一角色重复授权不行（必须先撤回）
        assertThatThrownBy(() -> service.grantApproval("B-APPR", QA, "alice-2", null))
                .isInstanceOf(BusinessRuleException.class);

        service.grantApproval("B-APPR", PROD, "bob", null);
        Release release = service.release("R-A2", "B-APPR", "qa");
        assertThat(release.getReleaseNo()).isEqualTo("R-A2");
    }

    @Test
    void withdrawnApprovalDoesNotCount() {
        declare("B-W");
        allConforming("B-W");
        service.grantApproval("B-W", QA, "alice", null);
        service.grantApproval("B-W", PROD, "bob", null);
        service.withdrawApproval("B-W", QA, "alice");
        assertThatThrownBy(() -> service.release("R-W", "B-W", "qa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("两个不同角色");

        // 撤回后可以重新审批
        service.grantApproval("B-W", QA, "alice", "重新审批");
        assertThat(service.release("R-W2", "B-W", "qa")).isNotNull();
    }

    @Test
    void missingResultBlocksRelease() {
        declare("B-MISS");
        service.submitResult("B-MISS", "I1", true, "100%", null, "qc");
        twoApprovals("B-MISS");
        assertThatThrownBy(() -> service.release("R-M", "B-MISS", "qa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("缺少当前有效结果");
    }

    // ----- 放行 / 幂等 / 快照 -----

    @Test
    void happyPathReleaseLocksEvidenceSnapshots() {
        declare("B-HAPPY");
        service.openDeviation("B-HAPPY", "D-H", "瑕疵", "qc");
        service.submitResult("B-HAPPY", "I1", true, "100%", null, "qc");
        service.submitResult("B-HAPPY", "I2", false, "边缘", "D-H", "qc");
        service.disposeDeviation("D-H", DispositionDecision.CONDITIONAL_ACCEPT, "限定用途", null, "qa");
        twoApprovals("B-HAPPY");

        Release release = service.release("R-H", "B-HAPPY", "qa-lead");
        assertThat(release.getEvidenceItems()).hasSize(3);
        assertThat(release.getEvidenceItems()).extracting(ReleaseEvidenceItem::getEvidenceType)
                .containsExactlyInAnyOrder("RESULT", "RESULT", "DEVIATION");

        Release locked = service.evidencePackage("R-H");
        ReleaseEvidenceItem dev = locked.getEvidenceItems().stream()
                .filter(e -> "DEVIATION".equals(e.getEvidenceType())).findFirst().orElseThrow();
        assertThat(dev.getDeviationNo()).isEqualTo("D-H");
        assertThat(dev.getDeviationDecision()).isEqualTo(DispositionDecision.CONDITIONAL_ACCEPT);
        assertThat(dev.getDeviationStatus()).isEqualTo(DeviationStatus.CLOSED);
    }

    @Test
    void releaseIsIdempotentByReleaseNo() {
        declare("B-IDEM");
        fullyRelease("B-IDEM", "R-IDEM");
        Release second = service.release("R-IDEM", "B-IDEM", "retry");
        assertThat(second.getReleaseNo()).isEqualTo("R-IDEM");
        assertThat(second.getId()).isEqualTo(service.evidencePackage("R-IDEM").getId());
    }

    @Test
    void releaseNoCannotBeReusedForAnotherBatch() {
        declare("B-I1");
        fullyRelease("B-I1", "R-SHARED");
        declare("B-I2");
        allConforming("B-I2");
        twoApprovals("B-I2");
        assertThatThrownBy(() -> service.release("R-SHARED", "B-I2", "qa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已用于其他批次");
    }

    @Test
    void batchCannotBeReleasedTwiceWithDifferentNos() {
        declare("B-2R");
        fullyRelease("B-2R", "R-2R-1");
        assertThatThrownBy(() -> service.release("R-2R-2", "B-2R", "qa"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已使用放行业务号");
    }

    // ----- 放行后不可变 -----

    @Test
    void afterReleaseQuantityResultsAndApprovalsAreImmutable() {
        declare("B-LOCK");
        fullyRelease("B-LOCK", "R-LOCK");

        assertThatThrownBy(() -> service.updateQuantity("B-LOCK", new BigDecimal("2000"), "t"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.submitResult("B-LOCK", "I1", true, "补录", null, "qc"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.openDeviation("B-LOCK", "D-LATE", "x", "qc"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.grantApproval("B-LOCK", "NEW_ROLE", "x", null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.withdrawApproval("B-LOCK", QA, "alice"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ----- 撤销 / 出库 -----

    @Test
    void revokeBeforeShipBlocksFurtherFlow() {
        declare("B-REV");
        fullyRelease("B-REV", "R-REV");
        ReleaseRevocation revocation = service.revokeRelease("R-REV", "发现记录错误", "qm");
        assertThat(revocation.isAlreadyShipped()).isFalse();
        assertThat(service.getBatch("B-REV").getStatus()).isEqualTo(BatchStatus.REVOKED);

        assertThatThrownBy(() -> service.ship("B-REV", "wh"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("禁止出库");
    }

    @Test
    void revokeAfterShipRecordsAlreadyShipped() {
        declare("B-SHIP");
        fullyRelease("B-SHIP", "R-SHIP");
        service.ship("B-SHIP", "wh");
        assertThat(service.getBatch("B-SHIP").getStatus()).isEqualTo(BatchStatus.SHIPPED);
        ReleaseRevocation revocation = service.revokeRelease("R-SHIP", "投诉", "qm");
        assertThat(revocation.isAlreadyShipped()).isTrue();
        assertThat(service.getBatch("B-SHIP").getStatus()).isEqualTo(BatchStatus.REVOKED);
    }

    @Test
    void cannotRevokeTwiceOrReleaseAfterRevoke() {
        declare("B-R2");
        fullyRelease("B-R2", "R-R2");
        service.revokeRelease("R-R2", "err", "qm");
        assertThatThrownBy(() -> service.revokeRelease("R-R2", "err2", "qm"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.release("R-R2B", "B-R2", "qa"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotShipUnreleasedBatch() {
        declare("B-U");
        allConforming("B-U");
        assertThatThrownBy(() -> service.ship("B-U", "wh"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("尚未放行");
    }

    // ----- 查询 -----

    @Test
    void currentConclusionReportsBlockersThenReleasable() {
        declare("B-Q");
        CurrentConclusion early = service.currentConclusion("B-Q");
        assertThat(early.releasable()).isFalse();
        assertThat(early.blockers()).isNotEmpty();
        assertThat(early.release()).isNull();

        fullyRelease("B-Q", "R-Q");
        CurrentConclusion done = service.currentConclusion("B-Q");
        assertThat(done.releasable()).isTrue();
        assertThat(done.blockers()).isEmpty();
        assertThat(done.release()).isNotNull();
    }

    @Test
    void historyCapturesFullVersionTrail() {
        declare("B-HIST");
        service.openDeviation("B-HIST", "D-HIST", "x", "qc");
        service.submitResult("B-HIST", "I1", false, "x", "D-HIST", "qc");
        service.disposeDeviation("D-HIST", DispositionDecision.CONDITIONAL_ACCEPT, "ok", null, "qa");
        service.submitResult("B-HIST", "I2", true, "ok", null, "qc");
        // 重新提交合格版本替代不合格版本（旧版本保留历史）
        service.submitResult("B-HIST", "I1", true, "100%", null, "qc");
        twoApprovals("B-HIST");
        service.release("R-HIST", "B-HIST", "qa");

        Batch batch = service.history("B-HIST");
        assertThat(batch.getEvents())
                .extracting(e -> e.getType().name())
                .contains("BATCH_DECLARED", "RESULT_SUBMITTED", "DEVIATION_OPENED",
                        "DEVIATION_DISPOSITIONED", "APPROVAL_GRANTED", "RELEASED");
        // 旧结果版本仍保留
        assertThat(batch.findItem("I1").orElseThrow().getResults()).hasSize(2);
    }

    @Test
    void unknownBatchRaisesNotFound() {
        assertThatThrownBy(() -> service.getBatch("NOPE"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(batchRepository.findByBatchNo("NOPE")).isEmpty();
    }
}
