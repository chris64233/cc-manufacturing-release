package com.chris64233.manufacturingrelease.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.manufacturingrelease.domain.ApprovalRole;
import com.chris64233.manufacturingrelease.service.ManufacturingReleaseService;
import com.chris64233.manufacturingrelease.web.Views.ApprovalView;
import com.chris64233.manufacturingrelease.web.Views.BatchView;
import com.chris64233.manufacturingrelease.web.Views.ConclusionView;
import com.chris64233.manufacturingrelease.web.Views.DeviationView;
import com.chris64233.manufacturingrelease.web.Views.EvidencePackageView;
import com.chris64233.manufacturingrelease.web.Views.InspectionResultView;
import com.chris64233.manufacturingrelease.web.Views.ItemHistoryView;
import com.chris64233.manufacturingrelease.web.Views.ReleaseView;

/**
 * 生产批次质量放行 REST 接口。
 */
@RestController
@RequestMapping("/api")
public class ManufacturingReleaseController {

    private final ManufacturingReleaseService service;

    public ManufacturingReleaseController(ManufacturingReleaseService service) {
        this.service = service;
    }

    // 批次声明 ----------------------------------------------------------

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchView declareBatch(@Valid @RequestBody ApiRequests.DeclareBatchRequest request) {
        return service.declareBatch(request);
    }

    @GetMapping("/batches/{batchNo}")
    public BatchView getBatch(@PathVariable String batchNo) {
        return service.getBatch(batchNo);
    }

    // 检验结果 ----------------------------------------------------------

    @PostMapping("/batches/{batchNo}/inspection-results")
    public InspectionResultView submitResult(@PathVariable String batchNo,
                                             @Valid @RequestBody ApiRequests.SubmitResultRequest request) {
        return service.submitResult(batchNo, request);
    }

    // 偏差单 ------------------------------------------------------------

    @PostMapping("/batches/{batchNo}/deviations")
    public DeviationView registerDeviation(@PathVariable String batchNo,
                                           @Valid @RequestBody ApiRequests.RegisterDeviationRequest request) {
        return service.registerDeviation(batchNo, request);
    }

    @PostMapping("/batches/{batchNo}/deviations/{deviationNo}/decision")
    public DeviationView decideDeviation(@PathVariable String batchNo,
                                         @PathVariable String deviationNo,
                                         @Valid @RequestBody ApiRequests.DecideDeviationRequest request) {
        return service.decideDeviation(batchNo, deviationNo, request);
    }

    // 审批 --------------------------------------------------------------

    @PostMapping("/batches/{batchNo}/approvals")
    public ApprovalView grantApproval(@PathVariable String batchNo,
                                      @Valid @RequestBody ApiRequests.GrantApprovalRequest request) {
        return service.grantApproval(batchNo, request);
    }

    @PostMapping("/batches/{batchNo}/approvals/{role}/withdraw")
    public ApprovalView withdrawApproval(@PathVariable String batchNo,
                                         @PathVariable ApprovalRole role,
                                         @Valid @RequestBody WithdrawBody body) {
        return service.withdrawApproval(batchNo,
                new ApiRequests.WithdrawApprovalRequest(role, body.withdrawnBy()));
    }

    /** 撤回审批请求体：仅需操作人。 */
    public record WithdrawBody(@jakarta.validation.constraints.NotBlank String withdrawnBy) {
    }

    // 放行 / 撤销 -------------------------------------------------------

    @PostMapping("/batches/{batchNo}/releases")
    public ReleaseView release(@PathVariable String batchNo,
                               @Valid @RequestBody ApiRequests.ReleaseRequest request) {
        return service.release(batchNo, request);
    }

    @PostMapping("/batches/{batchNo}/revocation")
    public ReleaseView revokeRelease(@PathVariable String batchNo,
                                     @Valid @RequestBody ApiRequests.RevokeReleaseRequest request) {
        return service.revokeRelease(batchNo, request);
    }

    /** 出库流转闸门：放行被撤销或未放行返回 409。 */
    @PostMapping("/batches/{batchNo}/outbound-check")
    public ResponseEntity<Void> outboundCheck(@PathVariable String batchNo) {
        service.assertOutboundAllowed(batchNo);
        return ResponseEntity.noContent().build();
    }

    // 查询 --------------------------------------------------------------

    @GetMapping("/batches/{batchNo}/conclusion")
    public ConclusionView conclusion(@PathVariable String batchNo) {
        return service.getConclusion(batchNo);
    }

    @GetMapping("/batches/{batchNo}/history")
    public List<ItemHistoryView> history(@PathVariable String batchNo) {
        return service.getVersionHistory(batchNo);
    }

    @GetMapping("/batches/{batchNo}/evidence-package")
    public EvidencePackageView evidencePackage(@PathVariable String batchNo) {
        return service.getEvidencePackage(batchNo);
    }

    @GetMapping("/releases/{releaseNo}")
    public ReleaseView getRelease(@PathVariable String releaseNo) {
        return service.getRelease(releaseNo);
    }
}
