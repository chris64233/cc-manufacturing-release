package com.chris64233.manufacturingrelease.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ApiRequests() {

    public record InspectionItemSpec(
            @NotBlank String itemCode,
            @NotBlank String itemName) {
    }

    public record DeclareBatchRequest(
            @NotBlank String batchNo,
            @NotBlank String productCode,
            @NotNull @Positive BigDecimal quantity,
            @NotEmpty List<InspectionItemSpec> inspectionItems) {
    }

    public record SubmitResultRequest(
            @NotBlank String itemCode,
            @NotBlank String resultValue,
            @NotNull Boolean conforming,
            /** 不合格结果必须提供已有偏差单号 */
            String deviationNo,
            @NotBlank String submittedBy) {
    }

    public record RegisterDeviationRequest(
            @NotBlank String deviationNo,
            @NotBlank String description) {
    }

    public record DecideDeviationRequest(
            @NotNull com.chris64233.manufacturingrelease.domain.Disposition disposition,
            String decisionComment,
            @NotBlank String decidedBy) {
    }

    public record GrantApprovalRequest(
            @NotNull com.chris64233.manufacturingrelease.domain.ApprovalRole role,
            @NotBlank String approver,
            String comment) {
    }

    public record WithdrawApprovalRequest(
            @NotNull com.chris64233.manufacturingrelease.domain.ApprovalRole role,
            @NotBlank String withdrawnBy) {
    }

    public record ReleaseRequest(
            @NotBlank String releaseNo,
            @NotBlank String releasedBy) {
    }

    public record RevokeReleaseRequest(
            @NotBlank String reason,
            @NotBlank String revokedBy) {
    }
}
