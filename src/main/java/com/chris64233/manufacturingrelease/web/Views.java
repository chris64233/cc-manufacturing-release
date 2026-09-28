package com.chris64233.manufacturingrelease.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 对外只读视图（不可变 record）。
 */
public final class Views {

    private Views() {
    }

    public record ResultView(int versionNo, boolean conforming, String valueText, String deviationNo,
                             boolean superseded, String submittedBy, Instant submittedAt) {
    }

    public record ItemView(String itemCode, String itemName, String specification,
                           ResultView currentResult, List<ResultView> results) {
    }

    public record DeviationView(String deviationNo, String description, String status, String decision,
                                String dispositionRemark, String reworkItemCode, String dispositionBy,
                                Instant openedAt, Instant dispositionedAt, Instant closedAt) {
    }

    public record ApprovalView(String roleCode, String approver, String comment, boolean withdrawn,
                               Instant grantedAt, Instant withdrawnAt) {
    }

    public record BatchView(String batchNo, String productCode, String productName, BigDecimal quantity,
                            String status, Instant declaredAt,
                            List<ItemView> items, List<DeviationView> deviations,
                            List<ApprovalView> approvals) {
    }

    public record EvidenceView(String evidenceType, String itemCode,
                               Long resultVersionId, Integer resultVersionNo, Boolean conforming,
                               String valueText, Long deviationId, String deviationNo,
                               String deviationStatus, String deviationDecision,
                               String submittedBy, Instant submittedAt) {
    }

    public record ReleaseView(String releaseNo, String batchNo, String releasedBy, Instant releasedAt,
                              List<EvidenceView> evidence) {
    }

    public record ReleaseRef(String releaseNo, Instant releasedAt) {
    }

    public record RevocationView(String releaseNo, String reason, String revokedBy, boolean alreadyShipped,
                                 Instant revokedAt) {
    }

    public record ConclusionView(BatchView batch, boolean releasable, List<String> blockers,
                                 ReleaseRef release, RevocationView revocation) {
    }

    public record EventView(String type, String detail, String actor, Instant occurredAt) {
    }

    public record HistoryView(BatchView batch, List<EventView> events) {
    }
}
