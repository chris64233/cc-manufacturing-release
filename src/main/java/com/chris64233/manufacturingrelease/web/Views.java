package com.chris64233.manufacturingrelease.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.chris64233.manufacturingrelease.domain.ApprovalRole;
import com.chris64233.manufacturingrelease.domain.BatchStatus;
import com.chris64233.manufacturingrelease.domain.Conformity;
import com.chris64233.manufacturingrelease.domain.DeviationStatus;
import com.chris64233.manufacturingrelease.domain.Disposition;
import com.chris64233.manufacturingrelease.domain.EvidenceType;

/**
 * 查询侧只读视图。
 */
public final class Views {

    private Views() {
    }

    public record BatchView(
            String batchNo,
            String productCode,
            BigDecimal quantity,
            BatchStatus status,
            Instant createdAt) {
    }

    public record InspectionResultView(
            String itemCode,
            String itemName,
            int version,
            String resultValue,
            Conformity conformity,
            boolean current,
            String deviationNo,
            String submittedBy,
            Instant submittedAt) {
    }

    public record DeviationView(
            String deviationNo,
            String description,
            DeviationStatus status,
            Disposition disposition,
            String decisionComment,
            String decidedBy,
            Instant decidedAt,
            Instant createdAt) {
    }

    public record ApprovalView(
            Long id,
            ApprovalRole role,
            String approver,
            String comment,
            boolean active,
            Instant grantedAt,
            Instant withdrawnAt) {
    }

    public record EvidenceView(
            EvidenceType evidenceType,
            Long sourceId,
            String sourceKey,
            int lockedVersion,
            String summary) {
    }

    public record RevocationView(
            String reason,
            String revokedBy,
            Instant revokedAt) {
    }

    public record ReleaseView(
            String releaseNo,
            String batchNo,
            String productCode,
            BigDecimal quantity,
            BatchStatus batchStatus,
            String releasedBy,
            Instant releasedAt,
            boolean revoked,
            RevocationView latestRevocation,
            List<EvidenceView> evidences) {
    }

    /** 单项检验当前状态：用于当前结论 */
    public record ItemConclusionView(
            String itemCode,
            String itemName,
            boolean hasCurrentResult,
            Integer currentVersion,
            Conformity conformity,
            String resultValue,
            String deviationNo) {
    }

    /**
     * 批次当前结论：可否放行及阻塞原因，以及当前检验/偏差/审批状态。
     * conclusion 取值：RELEASED / RELEASE_REVOKED / RELEASABLE / BLOCKED。
     */
    public record ConclusionView(
            String batchNo,
            BatchStatus status,
            String conclusion,
            List<String> blockingReasons,
            List<ItemConclusionView> items,
            List<DeviationView> deviations,
            List<ApprovalRole> activeApprovalRoles) {
    }

    public record ItemHistoryView(
            String itemCode,
            String itemName,
            List<InspectionResultView> results) {
    }

    /**
     * 批次证据包：放行记录与锁定证据、撤销事件、当前结论、全部检验结果版本历史。
     */
    public record EvidencePackageView(
            BatchView batch,
            ReleaseView release,
            ConclusionView currentConclusion,
            List<ItemHistoryView> versionHistory) {
    }
}
