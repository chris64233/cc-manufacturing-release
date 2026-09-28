package com.chris64233.manufacturingrelease.web;

import com.chris64233.manufacturingrelease.domain.Batch;
import com.chris64233.manufacturingrelease.domain.Deviation;
import com.chris64233.manufacturingrelease.domain.DispositionDecision;
import com.chris64233.manufacturingrelease.domain.InspectionResultVersion;
import com.chris64233.manufacturingrelease.domain.Release;
import com.chris64233.manufacturingrelease.domain.ReleaseApproval;
import com.chris64233.manufacturingrelease.domain.ReleaseRevocation;
import com.chris64233.manufacturingrelease.service.QualityReleaseService;
import com.chris64233.manufacturingrelease.service.QualityReleaseService.CurrentConclusion;
import com.chris64233.manufacturingrelease.web.Requests.ApprovalRequest;
import com.chris64233.manufacturingrelease.web.Requests.DeclareBatchRequest;
import com.chris64233.manufacturingrelease.web.Requests.DispositionRequest;
import com.chris64233.manufacturingrelease.web.Requests.OpenDeviationRequest;
import com.chris64233.manufacturingrelease.web.Requests.ReleaseRequest;
import com.chris64233.manufacturingrelease.web.Requests.RevokeRequest;
import com.chris64233.manufacturingrelease.web.Requests.ShipRequest;
import com.chris64233.manufacturingrelease.web.Requests.SubmitResultRequest;
import com.chris64233.manufacturingrelease.web.Requests.UpdateQuantityRequest;
import com.chris64233.manufacturingrelease.web.Requests.WithdrawApprovalRequest;
import com.chris64233.manufacturingrelease.web.Views.ApprovalView;
import com.chris64233.manufacturingrelease.web.Views.BatchView;
import com.chris64233.manufacturingrelease.web.Views.ConclusionView;
import com.chris64233.manufacturingrelease.web.Views.DeviationView;
import com.chris64233.manufacturingrelease.web.Views.HistoryView;
import com.chris64233.manufacturingrelease.web.Views.ReleaseView;
import com.chris64233.manufacturingrelease.web.Views.ResultView;
import com.chris64233.manufacturingrelease.web.Views.RevocationView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 生产批次质量放行 HTTP 接口。
 */
@RestController
@RequestMapping("/api")
public class QualityReleaseController {

    private final QualityReleaseService service;
    private final ViewAssembler views;

    public QualityReleaseController(QualityReleaseService service, ViewAssembler views) {
        this.service = service;
        this.views = views;
    }

    // ----- 批次 -----

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchView declareBatch(@Valid @RequestBody DeclareBatchRequest request) {
        Batch batch = service.declareBatch(request.batchNo(), request.productCode(), request.productName(),
                request.quantity(), request.toDeclarations(), request.actor());
        return views.batch(batch);
    }

    @GetMapping("/batches/{batchNo}")
    public BatchView getBatch(@PathVariable String batchNo) {
        return views.batch(service.getBatch(batchNo));
    }

    @PutMapping("/batches/{batchNo}/quantity")
    public BatchView updateQuantity(@PathVariable String batchNo,
                                    @Valid @RequestBody UpdateQuantityRequest request) {
        return views.batch(service.updateQuantity(batchNo, request.quantity(), request.actor()));
    }

    // ----- 检验结果 -----

    @PostMapping("/batches/{batchNo}/results")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultView submitResult(@PathVariable String batchNo,
                                   @Valid @RequestBody SubmitResultRequest request) {
        InspectionResultVersion result = service.submitResult(batchNo, request.itemCode(),
                request.conforming(), request.valueText(), request.deviationNo(), request.actor());
        return views.result(result);
    }

    // ----- 偏差 -----

    @PostMapping("/batches/{batchNo}/deviations")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviationView openDeviation(@PathVariable String batchNo,
                                       @Valid @RequestBody OpenDeviationRequest request) {
        Deviation deviation = service.openDeviation(batchNo, request.deviationNo(),
                request.description(), request.actor());
        return views.deviation(deviation);
    }

    @PostMapping("/deviations/{deviationNo}/disposition")
    public DeviationView dispose(@PathVariable String deviationNo,
                                 @Valid @RequestBody DispositionRequest request) {
        DispositionDecision decision = parseDecision(request.decision());
        Deviation deviation = service.disposeDeviation(deviationNo, decision,
                request.remark(), request.reworkItemCode(), request.actor());
        return views.deviation(deviation);
    }

    private static DispositionDecision parseDecision(String raw) {
        try {
            return DispositionDecision.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "未知处置决定: " + raw + "，允许 REWORK / SCRAP / CONDITIONAL_ACCEPT");
        }
    }

    // ----- 审批 -----

    @PostMapping("/batches/{batchNo}/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalView grantApproval(@PathVariable String batchNo,
                                      @Valid @RequestBody ApprovalRequest request) {
        ReleaseApproval approval = service.grantApproval(batchNo, request.roleCode(),
                request.approver(), request.comment());
        return views.approval(approval);
    }

    @PostMapping("/batches/{batchNo}/approvals/withdrawals")
    public ApprovalView withdrawApproval(@PathVariable String batchNo,
                                         @Valid @RequestBody WithdrawApprovalRequest request) {
        return views.approval(service.withdrawApproval(batchNo, request.roleCode(), request.actor()));
    }

    // ----- 放行 / 撤销 / 出库 -----

    @PostMapping("/batches/{batchNo}/releases")
    public ReleaseView release(@PathVariable String batchNo,
                               @Valid @RequestBody ReleaseRequest request) {
        Release release = service.release(request.releaseNo(), batchNo, request.actor());
        return views.release(release);
    }

    @PostMapping("/releases/{releaseNo}/revocations")
    @ResponseStatus(HttpStatus.CREATED)
    public RevocationView revoke(@PathVariable String releaseNo,
                                 @Valid @RequestBody RevokeRequest request) {
        ReleaseRevocation revocation =
                service.revokeRelease(releaseNo, request.reason(), request.actor());
        return views.revocation(revocation);
    }

    @PostMapping("/batches/{batchNo}/ship")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void ship(@PathVariable String batchNo, @Valid @RequestBody ShipRequest request) {
        service.ship(batchNo, request.actor());
    }

    // ----- 查询 -----

    @GetMapping("/batches/{batchNo}/conclusion")
    public ConclusionView conclusion(@PathVariable String batchNo) {
        CurrentConclusion conclusion = service.currentConclusion(batchNo);
        return views.conclusion(conclusion);
    }

    @GetMapping("/releases/{releaseNo}/evidence")
    public ReleaseView evidence(@PathVariable String releaseNo) {
        return views.release(service.evidencePackage(releaseNo));
    }

    @GetMapping("/batches/{batchNo}/history")
    public HistoryView history(@PathVariable String batchNo) {
        return views.history(service.history(batchNo));
    }
}
