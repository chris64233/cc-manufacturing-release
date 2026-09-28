package com.chris64233.manufacturingrelease.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.manufacturingrelease.domain.ApprovalRole;
import com.chris64233.manufacturingrelease.domain.BatchRelease;
import com.chris64233.manufacturingrelease.domain.BatchStatus;
import com.chris64233.manufacturingrelease.domain.Conformity;
import com.chris64233.manufacturingrelease.domain.Deviation;
import com.chris64233.manufacturingrelease.domain.DeviationStatus;
import com.chris64233.manufacturingrelease.domain.Disposition;
import com.chris64233.manufacturingrelease.domain.EvidenceType;
import com.chris64233.manufacturingrelease.domain.InspectionItem;
import com.chris64233.manufacturingrelease.domain.InspectionResult;
import com.chris64233.manufacturingrelease.domain.ProductionBatch;
import com.chris64233.manufacturingrelease.domain.ReleaseApproval;
import com.chris64233.manufacturingrelease.domain.ReleaseEvidence;
import com.chris64233.manufacturingrelease.domain.ReleaseRevocation;
import com.chris64233.manufacturingrelease.repository.BatchReleaseRepository;
import com.chris64233.manufacturingrelease.repository.DeviationRepository;
import com.chris64233.manufacturingrelease.repository.InspectionItemRepository;
import com.chris64233.manufacturingrelease.repository.InspectionResultRepository;
import com.chris64233.manufacturingrelease.repository.ProductionBatchRepository;
import com.chris64233.manufacturingrelease.repository.ReleaseApprovalRepository;
import com.chris64233.manufacturingrelease.repository.ReleaseRevocationRepository;
import com.chris64233.manufacturingrelease.web.ApiRequests;
import com.chris64233.manufacturingrelease.web.Views;
import com.chris64233.manufacturingrelease.web.Views.ApprovalView;
import com.chris64233.manufacturingrelease.web.Views.BatchView;
import com.chris64233.manufacturingrelease.web.Views.ConclusionView;
import com.chris64233.manufacturingrelease.web.Views.DeviationView;
import com.chris64233.manufacturingrelease.web.Views.EvidencePackageView;
import com.chris64233.manufacturingrelease.web.Views.EvidenceView;
import com.chris64233.manufacturingrelease.web.Views.InspectionResultView;
import com.chris64233.manufacturingrelease.web.Views.ItemConclusionView;
import com.chris64233.manufacturingrelease.web.Views.ItemHistoryView;
import com.chris64233.manufacturingrelease.web.Views.ReleaseView;
import com.chris64233.manufacturingrelease.web.Views.RevocationView;

/**
 * 生产批次质量放行领域服务。
 *
 * <p>并发控制：所有修改批次数据的操作（补录检验结果、偏差处置、审批/撤回、放行、撤销）
 * 在事务开始即对批次行加悲观写锁（{@code lockByBatchNo}），与放行事务严格串行。
 * 放行事务在持有批次锁后，再按固定顺序（检验项目 → 当前检验结果 → 偏差 → 有效审批）
 * 对依据行加锁并在同一事务内完成全部校验与快照写入，因此并发补录结果或撤回审批
 * 不可能在放行校验之后改变依据，不会产生依据不完整的已放行批次。</p>
 */
@Service
public class ManufacturingReleaseService {

    private final ProductionBatchRepository batchRepository;
    private final InspectionItemRepository itemRepository;
    private final InspectionResultRepository resultRepository;
    private final DeviationRepository deviationRepository;
    private final ReleaseApprovalRepository approvalRepository;
    private final BatchReleaseRepository releaseRepository;
    private final ReleaseRevocationRepository revocationRepository;

    public ManufacturingReleaseService(ProductionBatchRepository batchRepository,
                                       InspectionItemRepository itemRepository,
                                       InspectionResultRepository resultRepository,
                                       DeviationRepository deviationRepository,
                                       ReleaseApprovalRepository approvalRepository,
                                       BatchReleaseRepository releaseRepository,
                                       ReleaseRevocationRepository revocationRepository) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.resultRepository = resultRepository;
        this.deviationRepository = deviationRepository;
        this.approvalRepository = approvalRepository;
        this.releaseRepository = releaseRepository;
        this.revocationRepository = revocationRepository;
    }

    // ------------------------------------------------------------------
    // 批次声明
    // ------------------------------------------------------------------

    @Transactional
    public BatchView declareBatch(ApiRequests.DeclareBatchRequest request) {
        if (batchRepository.existsByBatchNo(request.batchNo())) {
            throw new BusinessRuleException("批次号已存在: " + request.batchNo());
        }
        Set<String> seenItemCodes = new HashSet<>();
        for (ApiRequests.InspectionItemSpec spec : request.inspectionItems()) {
            if (!seenItemCodes.add(spec.itemCode())) {
                throw new BusinessRuleException("批次内检验项目编码重复: " + spec.itemCode());
            }
        }
        ProductionBatch batch = new ProductionBatch(request.batchNo(), request.productCode(), request.quantity());
        for (ApiRequests.InspectionItemSpec spec : request.inspectionItems()) {
            batch.addItem(new InspectionItem(batch, spec.itemCode(), spec.itemName()));
        }
        ProductionBatch saved = batchRepository.save(batch);
        return toBatchView(saved);
    }

    // ------------------------------------------------------------------
    // 检验结果（带版本）
    // ------------------------------------------------------------------

    @Transactional
    public InspectionResultView submitResult(String batchNo, ApiRequests.SubmitResultRequest request) {
        ProductionBatch batch = lockPendingBatch(batchNo);
        InspectionItem item = itemRepository.findByBatchIdAndItemCode(batch.getId(), request.itemCode())
                .orElseThrow(() -> new NotFoundException(
                        "批次 " + batchNo + " 下不存在检验项目: " + request.itemCode()));

        Deviation deviation = null;
        Conformity conformity = Boolean.TRUE.equals(request.conforming())
                ? Conformity.CONFORMING : Conformity.NONCONFORMING;
        if (conformity == Conformity.NONCONFORMING) {
            if (request.deviationNo() == null || request.deviationNo().isBlank()) {
                throw new BusinessRuleException("不合格检验结果必须关联偏差单: " + request.itemCode());
            }
            deviation = deviationRepository.findByDeviationNo(request.deviationNo())
                    .orElseThrow(() -> new NotFoundException("偏差单不存在: " + request.deviationNo()));
            if (!deviation.getBatch().getId().equals(batch.getId())) {
                throw new BusinessRuleException(
                        "偏差单 " + request.deviationNo() + " 不属于批次 " + batchNo);
            }
        } else if (request.deviationNo() != null && !request.deviationNo().isBlank()) {
            throw new BusinessRuleException("合格检验结果不允许关联偏差单: " + request.itemCode());
        }

        int nextVersion = resultRepository.findMaxVersionByItemId(item.getId()) + 1;
        List<InspectionResult> history = resultRepository.findByItemIdOrderByVersion(item.getId());
        history.stream().filter(InspectionResult::isCurrent).forEach(InspectionResult::markSuperseded);

        InspectionResult result = new InspectionResult(item, nextVersion, request.resultValue(), conformity,
                deviation, request.submittedBy());
        item.addResult(result);
        resultRepository.save(result);
        return toResultView(result);
    }

    // ------------------------------------------------------------------
    // 偏差单
    // ------------------------------------------------------------------

    @Transactional
    public DeviationView registerDeviation(String batchNo, ApiRequests.RegisterDeviationRequest request) {
        ProductionBatch batch = lockPendingBatch(batchNo);
        if (deviationRepository.existsByDeviationNo(request.deviationNo())) {
            throw new BusinessRuleException("偏差单号已存在: " + request.deviationNo());
        }
        Deviation deviation = new Deviation(request.deviationNo(), batch, request.description());
        return toDeviationView(deviationRepository.save(deviation));
    }

    @Transactional
    public DeviationView decideDeviation(String batchNo, String deviationNo,
                                         ApiRequests.DecideDeviationRequest request) {
        ProductionBatch batch = lockPendingBatch(batchNo);
        Deviation deviation = getBatchDeviation(batch, deviationNo);
        deviation.decide(request.disposition(), request.decisionComment(), request.decidedBy());
        return toDeviationView(deviation);
    }

    // ------------------------------------------------------------------
    // 审批
    // ------------------------------------------------------------------

    @Transactional
    public ApprovalView grantApproval(String batchNo, ApiRequests.GrantApprovalRequest request) {
        ProductionBatch batch = lockPendingBatch(batchNo);
        boolean roleAlreadyActive = approvalRepository.findActiveByBatchId(batch.getId()).stream()
                .anyMatch(a -> a.getRole() == request.role());
        if (roleAlreadyActive) {
            throw new BusinessRuleException(
                    "角色 " + request.role() + " 已有有效审批；如需变更请先撤回原审批");
        }
        ReleaseApproval approval = new ReleaseApproval(batch, request.role(), request.approver(),
                request.comment());
        return toApprovalView(approvalRepository.save(approval));
    }

    @Transactional
    public ApprovalView withdrawApproval(String batchNo, ApiRequests.WithdrawApprovalRequest request) {
        ProductionBatch batch = lockPendingBatch(batchNo);
        List<ReleaseApproval> active = approvalRepository.findActiveByBatchId(batch.getId()).stream()
                .filter(a -> a.getRole() == request.role())
                .toList();
        if (active.isEmpty()) {
            throw new BusinessRuleException("角色 " + request.role() + " 没有可撤回的有效审批");
        }
        active.forEach(a -> a.withdraw());
        return toApprovalView(active.get(0));
    }

    // ------------------------------------------------------------------
    // 放行
    // ------------------------------------------------------------------

    @Transactional
    public ReleaseView release(String batchNo, ApiRequests.ReleaseRequest request) {
        // 幂等：同一放行业务号直接返回首次放行结果（即使批次事后被撤销，仍返回同一记录）。
        Optional<BatchRelease> replay = releaseRepository.findWithEvidencesByReleaseNo(request.releaseNo());
        if (replay.isPresent()) {
            BatchRelease existing = replay.get();
            if (!existing.getBatch().getBatchNo().equals(batchNo)) {
                throw new BusinessRuleException(
                        "放行业务号 " + request.releaseNo() + " 已用于其他批次");
            }
            return toReleaseView(existing);
        }

        // 批次行锁：与补录结果/撤回审批/撤销等一切批次变更串行。
        ProductionBatch batch = batchRepository.lockByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchNo));
        // 持锁后再次检查幂等键：两个相同业务号的并发放行，后来者直接重放首次结果。
        Optional<BatchRelease> concurrent = releaseRepository.findByReleaseNo(request.releaseNo());
        if (concurrent.isPresent()) {
            BatchRelease other = concurrent.get();
            if (!other.getBatch().getId().equals(batch.getId())) {
                throw new BusinessRuleException(
                        "放行业务号 " + request.releaseNo() + " 已用于其他批次");
            }
            return toReleaseView(releaseRepository.findWithEvidencesByReleaseNo(request.releaseNo())
                    .orElseThrow());
        }
        if (batch.getStatus() != BatchStatus.PENDING_RELEASE) {
            throw new BusinessRuleException("批次当前状态不允许放行: " + batch.getStatus()
                    + "（放行后只能登记撤销）");
        }

        Long batchId = batch.getId();

        // 固定顺序对全部依据行加锁，消除放行与并发变更之间的检查窗口。
        List<InspectionItem> items = itemRepository.lockByBatchId(batchId);
        List<InspectionResult> currentResults = resultRepository.lockCurrentByBatchId(batchId);
        List<Deviation> deviations = deviationRepository.lockByBatchId(batchId);
        List<ReleaseApproval> activeApprovals = approvalRepository.lockActiveByBatchId(batchId);

        Map<Long, InspectionResult> currentByItem = currentResults.stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        List<String> reasons = new ArrayList<>();

        // 1) 每个所需检验项目都必须有且仅有一个当前有效结果。
        for (InspectionItem item : items) {
            InspectionResult current = currentByItem.get(item.getId());
            if (current == null) {
                reasons.add("检验项目缺少当前有效结果: " + item.getItemCode());
                continue;
            }
            if (current.getConformity() == Conformity.NONCONFORMING && current.getDeviation() == null) {
                reasons.add("检验项目当前不合格结果未关联偏差单: " + item.getItemCode());
            }
        }
        if (currentResults.size() > items.size()) {
            reasons.add("当前有效结果数量异常，禁止放行");
        }

        // 2) 批次全部偏差必须是允许放行的终态处理（返工/有条件接受）。
        for (Deviation deviation : deviations) {
            if (!deviation.isReleasePermitting()) {
                String state = deviation.getStatus() == DeviationStatus.OPEN
                        ? "尚未处置"
                        : "处置结论为 " + deviation.getDisposition() + "（不允许放行）";
                reasons.add("偏差单未获得允许放行的终态处理: " + deviation.getDeviationNo() + "（" + state + "）");
            }
        }

        // 3) 必须有两个不同角色的有效审批。
        Set<ApprovalRole> activeRoles = activeApprovals.stream()
                .map(ReleaseApproval::getRole)
                .collect(Collectors.toCollection(HashSet::new));
        if (!activeRoles.containsAll(EnumSet.allOf(ApprovalRole.class))) {
            reasons.add("缺少两个不同角色的有效审批，当前有效角色: "
                    + (activeRoles.isEmpty() ? "无" : activeRoles));
        }

        if (!reasons.isEmpty()) {
            throw new BusinessRuleException("批次不满足放行条件：" + String.join("；", reasons));
        }

        // 校验通过：在同一事务内创建放行记录并逐行锁定本次使用的依据版本。
        BatchRelease release = new BatchRelease(request.releaseNo(), batch, request.releasedBy());

        for (InspectionItem item : items) {
            InspectionResult current = currentByItem.get(item.getId());
            String summary = "检验项目 " + item.getItemCode() + " v" + current.getVersion()
                    + " 判定=" + current.getConformity()
                    + (current.getDeviation() != null
                            ? " 偏差单=" + current.getDeviation().getDeviationNo() : "")
                    + " 结果=" + current.getResultValue();
            release.addEvidence(new ReleaseEvidence(release, EvidenceType.INSPECTION_RESULT,
                    current.getId(), item.getItemCode(), current.getVersion(), summary));
        }
        for (Deviation deviation : deviations) {
            String summary = "偏差单 " + deviation.getDeviationNo()
                    + " 处置=" + deviation.getDisposition()
                    + " 处置人=" + deviation.getDecidedBy();
            release.addEvidence(new ReleaseEvidence(release, EvidenceType.DEVIATION,
                    deviation.getId(), deviation.getDeviationNo(), 1, summary));
        }
        for (ReleaseApproval approval : activeApprovals) {
            String summary = "审批角色 " + approval.getRole()
                    + " 审批人=" + approval.getApprover();
            release.addEvidence(new ReleaseEvidence(release, EvidenceType.APPROVAL,
                    approval.getId(), approval.getRole().name(), 1, summary));
        }

        batch.setStatus(BatchStatus.RELEASED);
        BatchRelease saved = releaseRepository.save(release);
        return toReleaseView(saved);
    }

    // ------------------------------------------------------------------
    // 撤销放行
    // ------------------------------------------------------------------

    @Transactional
    public ReleaseView revokeRelease(String batchNo, ApiRequests.RevokeReleaseRequest request) {
        // 批次行锁：与放行串行，确保放行/撤销状态清晰。
        ProductionBatch batch = batchRepository.lockByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchNo));
        BatchRelease release = releaseRepository.findByBatchId(batch.getId())
                .orElseThrow(() -> new BusinessRuleException("批次尚未放行，不能撤销: " + batchNo));
        if (batch.getStatus() == BatchStatus.RELEASE_REVOKED) {
            throw new BusinessRuleException("批次放行已撤销，不能重复撤销: " + batchNo);
        }
        revocationRepository.save(new ReleaseRevocation(release, request.reason(), request.revokedBy()));
        batch.setStatus(BatchStatus.RELEASE_REVOKED);
        return toReleaseView(release);
    }

    /**
     * 出库（继续流转）闸门：仅已放行且未撤销的批次可通过。
     */
    @Transactional(readOnly = true)
    public void assertOutboundAllowed(String batchNo) {
        ProductionBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchNo));
        if (batch.getStatus() != BatchStatus.RELEASED) {
            throw new BusinessRuleException("批次 " + batchNo + " 当前状态为 " + batch.getStatus()
                    + "，阻止继续流转（出库）");
        }
    }

    // ------------------------------------------------------------------
    // 查询：当前结论 / 证据包 / 版本历史
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BatchView getBatch(String batchNo) {
        return toBatchView(requireBatch(batchNo));
    }

    @Transactional(readOnly = true)
    public ConclusionView getConclusion(String batchNo) {
        ProductionBatch batch = requireBatch(batchNo);
        return evaluate(batch);
    }

    @Transactional(readOnly = true)
    public List<ItemHistoryView> getVersionHistory(String batchNo) {
        ProductionBatch batch = requireBatch(batchNo);
        return itemRepository.findWithResultsByBatchId(batch.getId()).stream()
                .map(this::toItemHistoryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReleaseView getRelease(String releaseNo) {
        BatchRelease release = releaseRepository.findWithEvidencesByReleaseNo(releaseNo)
                .orElseThrow(() -> new NotFoundException("放行记录不存在: " + releaseNo));
        return toReleaseView(release);
    }

    @Transactional(readOnly = true)
    public EvidencePackageView getEvidencePackage(String batchNo) {
        ProductionBatch batch = requireBatch(batchNo);
        List<ItemHistoryView> history = itemRepository.findWithResultsByBatchId(batch.getId()).stream()
                .map(this::toItemHistoryView)
                .toList();
        ReleaseView releaseView = releaseRepository.findByBatchId(batch.getId())
                .map(r -> releaseRepository.findWithEvidencesByReleaseNo(r.getReleaseNo()).orElseThrow())
                .map(this::toReleaseView)
                .orElse(null);
        return new EvidencePackageView(toBatchView(batch), releaseView, evaluate(batch), history);
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    private ProductionBatch requireBatch(String batchNo) {
        return batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchNo));
    }

    /** 加批次写锁，并要求批次仍处于可变更状态（未放行、未撤销）。 */
    private ProductionBatch lockPendingBatch(String batchNo) {
        ProductionBatch batch = batchRepository.lockByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchNo));
        if (batch.getStatus() != BatchStatus.PENDING_RELEASE) {
            throw new BusinessRuleException("批次已" + (batch.getStatus() == BatchStatus.RELEASED ? "放行" : "撤销放行")
                    + "，数量与放行依据不可修改: " + batchNo);
        }
        return batch;
    }

    private Deviation getBatchDeviation(ProductionBatch batch, String deviationNo) {
        Deviation deviation = deviationRepository.findByDeviationNo(deviationNo)
                .orElseThrow(() -> new NotFoundException("偏差单不存在: " + deviationNo));
        if (!deviation.getBatch().getId().equals(batch.getId())) {
            throw new BusinessRuleException("偏差单 " + deviationNo + " 不属于批次 " + batch.getBatchNo());
        }
        return deviation;
    }

    /**
     * 依据当前数据计算批次结论与阻塞原因（读已提交下的即时快照）。
     */
    private ConclusionView evaluate(ProductionBatch batch) {
        Long batchId = batch.getId();
        List<InspectionItem> items = itemRepository.findWithResultsByBatchId(batchId);
        List<Deviation> deviations = deviationRepository.findByBatchId(batchId);
        Set<ApprovalRole> activeRoles = approvalRepository.findActiveByBatchId(batchId).stream()
                .map(ReleaseApproval::getRole)
                .collect(Collectors.toCollection(HashSet::new));

        List<String> reasons = new ArrayList<>();
        List<ItemConclusionView> itemViews = new ArrayList<>();

        for (InspectionItem item : items) {
            InspectionResult current = item.getResults().stream()
                    .filter(InspectionResult::isCurrent)
                    .findFirst()
                    .orElse(null);
            if (current == null) {
                reasons.add("检验项目缺少当前有效结果: " + item.getItemCode());
                itemViews.add(new ItemConclusionView(item.getItemCode(), item.getItemName(),
                        false, null, null, null, null));
            } else {
                if (current.getConformity() == Conformity.NONCONFORMING && current.getDeviation() == null) {
                    reasons.add("检验项目当前不合格结果未关联偏差单: " + item.getItemCode());
                }
                itemViews.add(new ItemConclusionView(item.getItemCode(), item.getItemName(),
                        true, current.getVersion(), current.getConformity(), current.getResultValue(),
                        current.getDeviation() != null ? current.getDeviation().getDeviationNo() : null));
            }
        }

        for (Deviation deviation : deviations) {
            if (!deviation.isReleasePermitting()) {
                String state = deviation.getStatus() == DeviationStatus.OPEN
                        ? "尚未处置"
                        : "处置结论为 " + deviation.getDisposition() + "（不允许放行）";
                reasons.add("偏差单未获得允许放行的终态处理: " + deviation.getDeviationNo() + "（" + state + "）");
            }
        }

        if (!activeRoles.containsAll(EnumSet.allOf(ApprovalRole.class))) {
            reasons.add("缺少两个不同角色的有效审批，当前有效角色: "
                    + (activeRoles.isEmpty() ? "无" : activeRoles));
        }

        String conclusion;
        if (batch.getStatus() == BatchStatus.RELEASED) {
            conclusion = "RELEASED";
        } else if (batch.getStatus() == BatchStatus.RELEASE_REVOKED) {
            conclusion = "RELEASE_REVOKED";
        } else {
            conclusion = reasons.isEmpty() ? "RELEASABLE" : "BLOCKED";
        }

        return new ConclusionView(batch.getBatchNo(), batch.getStatus(), conclusion,
                List.copyOf(reasons), itemViews,
                deviations.stream().map(this::toDeviationView).toList(),
                List.copyOf(activeRoles));
    }

    // ------------------------------------------------------------------
    // 视图映射
    // ------------------------------------------------------------------

    private BatchView toBatchView(ProductionBatch batch) {
        return new BatchView(batch.getBatchNo(), batch.getProductCode(), batch.getQuantity(),
                batch.getStatus(), batch.getCreatedAt());
    }

    private InspectionResultView toResultView(InspectionResult result) {
        return new InspectionResultView(
                result.getItem().getItemCode(),
                result.getItem().getItemName(),
                result.getVersion(),
                result.getResultValue(),
                result.getConformity(),
                result.isCurrent(),
                result.getDeviation() != null ? result.getDeviation().getDeviationNo() : null,
                result.getSubmittedBy(),
                result.getSubmittedAt());
    }

    private ItemHistoryView toItemHistoryView(InspectionItem item) {
        List<InspectionResultView> orderedResults = item.getResults().stream()
                .sorted(java.util.Comparator.comparingInt(InspectionResult::getVersion))
                .map(this::toResultView)
                .toList();
        return new ItemHistoryView(item.getItemCode(), item.getItemName(), orderedResults);
    }

    private DeviationView toDeviationView(Deviation deviation) {
        return new DeviationView(deviation.getDeviationNo(), deviation.getDescription(),
                deviation.getStatus(), deviation.getDisposition(), deviation.getDecisionComment(),
                deviation.getDecidedBy(), deviation.getDecidedAt(), deviation.getCreatedAt());
    }

    private ApprovalView toApprovalView(ReleaseApproval approval) {
        return new ApprovalView(approval.getId(), approval.getRole(), approval.getApprover(),
                approval.getComment(), approval.isActive(), approval.getGrantedAt(),
                approval.getWithdrawnAt());
    }

    private ReleaseView toReleaseView(BatchRelease release) {
        List<EvidenceView> evidenceViews = release.getEvidences().stream()
                .map(e -> new EvidenceView(e.getEvidenceType(), e.getSourceId(), e.getSourceKey(),
                        e.getLockedVersion(), e.getSummary()))
                .toList();
        boolean revoked = revocationRepository.existsByReleaseId(release.getId());
        RevocationView latestRevocation = null;
        if (revoked) {
            ReleaseRevocation revocation =
                    revocationRepository.findByReleaseIdOrderByRevokedAtDesc(release.getId()).get(0);
            latestRevocation = new RevocationView(revocation.getReason(), revocation.getRevokedBy(),
                    revocation.getRevokedAt());
        }
        return new ReleaseView(release.getReleaseNo(), release.getBatch().getBatchNo(),
                release.getProductCode(), release.getQuantity(), release.getBatch().getStatus(),
                release.getReleasedBy(), release.getReleasedAt(), revoked, latestRevocation,
                evidenceViews);
    }
}
