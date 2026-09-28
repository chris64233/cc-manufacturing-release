package com.chris64233.manufacturingrelease.web;

import com.chris64233.manufacturingrelease.service.QualityReleaseService.ItemDeclaration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public final class Requests {

    private Requests() {
    }

    public record ItemRequest(@NotBlank String itemCode,
                              String itemName,
                              String specification) {
    }

    public record DeclareBatchRequest(@NotBlank String batchNo,
                                      @NotBlank String productCode,
                                      String productName,
                                      @NotNull @Positive BigDecimal quantity,
                                      @NotEmpty List<@Valid ItemRequest> items,
                                      @NotBlank String actor) {
        public List<ItemDeclaration> toDeclarations() {
            return items.stream()
                    .map(i -> new ItemDeclaration(i.itemCode(), i.itemName(), i.specification()))
                    .toList();
        }
    }

    public record UpdateQuantityRequest(@NotNull @Positive BigDecimal quantity,
                                        @NotBlank String actor) {
    }

    public record SubmitResultRequest(@NotBlank String itemCode,
                                      @NotNull Boolean conforming,
                                      String valueText,
                                      String deviationNo,
                                      @NotBlank String actor) {
    }

    public record OpenDeviationRequest(@NotBlank String deviationNo,
                                       String description,
                                       @NotBlank String actor) {
    }

    public record DispositionRequest(@NotBlank String decision,
                                     String remark,
                                     String reworkItemCode,
                                     @NotBlank String actor) {
    }

    public record ApprovalRequest(@NotBlank String roleCode,
                                  @NotBlank String approver,
                                  String comment) {
    }

    public record WithdrawApprovalRequest(@NotBlank String roleCode,
                                          @NotBlank String actor) {
    }

    public record ReleaseRequest(@NotBlank String releaseNo,
                                 @NotBlank String actor) {
    }

    public record RevokeRequest(@NotBlank String reason,
                                @NotBlank String actor) {
    }

    public record ShipRequest(@NotBlank String actor) {
    }
}
