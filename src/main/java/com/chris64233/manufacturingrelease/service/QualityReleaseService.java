package com.chris64233.manufacturingrelease.service;

import com.chris64233.manufacturingrelease.domain.Batch;
import com.chris64233.manufacturingrelease.domain.BatchEvent;
import com.chris64233.manufacturingrelease.domain.BatchRepository;
import com.chris64233.manufacturingrelease.domain.BatchStatus;
import com.chris64233.manufacturingrelease.domain.Deviation;
import com.chris64233.manufacturingrelease.domain.DeviationRepository;
import com.chris64233.manufacturingrelease.domain.DeviationStatus;
import com.chris64233.manufacturingrelease.domain.DispositionDecision;
import com.chris64233.manufacturingrelease.domain.EventType;
import com.chris64233.manufacturingrelease.domain.InspectionItem;
import com.chris64233.manufacturingrelease.domain.InspectionResultVersion;
import com.chris64233.manufacturingrelease.domain.Release;
import com.chris64233.manufacturingrelease.domain.ReleaseApproval;
import com.chris64233.manufacturingrelease.domain.ReleaseEvidenceItem;
import com.chris64233.manufacturingrelease.domain.ReleaseRepository;
import com.chris64233.manufacturingrelease.domain.ReleaseRevocation;
import com.chris64233.manufacturingrelease.domain.ReleaseRevocationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 质量放行领域服务。
 *
 * <p>所有改变批次资料或状态的方法都在单个事务内先对批次行加悲观写锁
 * （{@link BatchRepository#findByBatchNoForUpdate}），使同一批次上的补录结果、
 * 审批撤回、放行等动作严格串行；放行事务内同时完成"依据校验 + 版本快照锁定 + 状态翻转"，
 * 因此不会产生依据不完整的已放行批次。
 */
@Service
public class QualityReleaseService {

    private final BatchRepository batchRepository;
    private final DeviationRepository deviationRepository;
    private final ReleaseRepository releaseRepository;
    private final ReleaseRevocationRepository revocationRepository;

    public QualityReleaseService(BatchRepository batchRepository,
                                 DeviationRepository deviationRepository,
                                 ReleaseRepository releaseRepository,
                                 ReleaseRevocationRepository revocationRepository) {
        this.batchRepository = batchRepository;
        this.deviationRepository = deviationRepository;
        this.releaseRepository = releaseRepository;
        this.revocationRepository = revocationRepository;
    }

    // ---------------------------------------------------------------------
    // 批次声明
    // ---------------------------------------------------------------------

    /** 声明生产批次：产品、数量、所需检验项目。 */
    @Transactional
    public Batch declareBatch(String batchNo, String productCode, String productName,
                              BigDecimal quantity, List<ItemDeclaration> items, String actor) {
        if (batchRepository.findByBatchNo(batchNo).isPresent()) {
            throw new BusinessRuleException("批次号已存在: " + batchNo);
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessRuleException("批次至少要声明一个检验项目");
        }
        Batch batch = new Batch(batchNo, productCode, productName, quantity);
        Set<String> seen = new LinkedHashSet<>();
        for (ItemDeclaration it : items) {
            if (!seen.add(it.itemCode())) {
                throw new BusinessRuleException("检验项目代码重复: " + it.itemCode());
            }
            batch.addItem(new InspectionItem(it.itemCode(), it.itemName(), it.specification()));
        }
        record(batch, EventType.BATCH_DECLARED,
                "声明批次，产品=" + productCode + "，数量=" + quantity + "，检验项目=" + seen.size(), actor);
        return batchRepository.save(batch);
    }

    /** 修改批次数量；放行（含撤销/出库）后不可修改。 */
    @Transactional
    public Batch updateQuantity(String batchNo, BigDecimal quantity, String actor) {
        Batch batch = lockBatch(batchNo);
        requireCreated(batch, "批次已放行，数量不可修改");
        BigDecimal old = batch.getQuantity();
        batch.setQuantity(quantity);
        record(batch, EventType.QUANTITY_UPDATED, "数量 " + old + " -> " + quantity, actor);
        return batch;
    }

    // ---------------------------------------------------------------------
    // 版本化检验结果
    // ---------------------------------------------------------------------

    /**
     * 提交检验结果（新版本）。同一项目旧版本被标记为已替代并保留历史。
     * 不合格结果必须关联一张本批次的、尚未关闭的偏差单。
     * 返工项目复验合格时自动关闭对应的返工偏差。
     */
    @Transactional
    public InspectionResultVersion submitResult(String batchNo, String itemCode, boolean conforming,
                                                String valueText, String deviationNo, String actor) {
        Batch batch = lockBatch(batchNo);
        requireCreated(batch, "批次已放行，检验结果不可补录");
        InspectionItem item = batch.findItem(itemCode)
                .orElseThrow(() -> new ResourceNotFoundException("检验项目不存在: " + itemCode));

        Deviation deviation = null;
        if (!conforming) {
            if (deviationNo == null || deviationNo.isBlank()) {
                throw new BusinessRuleException("不合格结果必须关联偏差单");
            }
            deviation = deviationRepository.findByDeviationNo(deviationNo)
                    .orElseThrow(() -> new ResourceNotFoundException("偏差单不存在: " + deviationNo));
            if (!deviation.getBatch().getId().equals(batch.getId())) {
                throw new BusinessRuleException("偏差单 " + deviationNo + " 不属于批次 " + batchNo);
            }
            if (deviation.getStatus() == DeviationStatus.CLOSED) {
                throw new BusinessRuleException(
                        "偏差单 " + deviationNo + " 已关闭，不能再作为新不合格结果的依据，请登记新偏差");
            }
        }

        int nextVersion = item.getResults().size() + 1;
        InspectionResultVersion version =
                item.submitResult(conforming, valueText, deviation, actor);
        record(batch, EventType.RESULT_SUBMITTED,
                "项目 " + itemCode + " 提交第 " + nextVersion + " 版结果，"
                        + (conforming ? "合格" : "不合格，关联偏差 " + deviationNo),
                actor);

        // 返工后复验合格：关闭等待该项目复验的返工偏差
        if (conforming) {
            for (Deviation d : batch.getDeviations()) {
                if (d.getStatus() == DeviationStatus.IN_DISPOSITION
                        && itemCode.equals(d.getReworkItemCode())) {
                    d.reworkVerified(itemCode);
                    record(batch, EventType.DEVIATION_CLOSED,
                            "返工复验合格，偏差 " + d.getDeviationNo() + " 关闭", actor);
                }
            }
        }
        return version;
    }

    // ---------------------------------------------------------------------
    // 偏差处置
    // ---------------------------------------------------------------------

    /** 登记偏差单。 */
    @Transactional
    public Deviation openDeviation(String batchNo, String deviationNo, String description, String actor) {
        Batch batch = lockBatch(batchNo);
        requireCreated(batch, "批次已放行，不能登记偏差");
        if (deviationRepository.existsByDeviationNo(deviationNo)) {
            throw new BusinessRuleException("偏差单号已存在: " + deviationNo);
        }
        Deviation deviation = new Deviation(batch, deviationNo, description);
        batch.getDeviations().add(deviation);
        record(batch, EventType.DEVIATION_OPENED, "登记偏差 " + deviationNo, actor);
        return deviation;
    }

    /**
     * 给出偏差处置：返工 / 报废 / 有条件接受。
     * 返工必须指定待复验的检验项目；报废与有条件接受直接进入终态关闭。
     */
    @Transactional
    public Deviation disposeDeviation(String deviationNo, DispositionDecision decision,
                                      String remark, String reworkItemCode, String actor) {
        Deviation deviation = deviationRepository.findByDeviationNo(deviationNo)
                .orElseThrow(() -> new ResourceNotFoundException("偏差单不存在: " + deviationNo));
        Batch batch = lockBatch(deviation.getBatch().getBatchNo());
        deviation = batch.getDeviations().stream()
                .filter(d -> d.getDeviationNo().equals(deviationNo)).findFirst().orElseThrow();

        if (deviation.getStatus() != DeviationStatus.OPEN) {
            throw new BusinessRuleException("偏差单 " + deviationNo + " 已处置，不能重复处置");
        }
        if (decision == DispositionDecision.REWORK) {
            if (reworkItemCode == null || reworkItemCode.isBlank()) {
                throw new BusinessRuleException("返工处置必须指定待复验的检验项目");
            }
            if (batch.findItem(reworkItemCode).isEmpty()) {
                throw new BusinessRuleException("待复验的检验项目不存在: " + reworkItemCode);
            }
        }
        deviation.applyDisposition(decision, remark, actor);
        if (decision == DispositionDecision.REWORK) {
            deviation.setReworkItemCode(reworkItemCode);
        }
        record(batch, EventType.DEVIATION_DISPOSITIONED,
                "偏差 " + deviationNo + " 处置决定：" + decision
                        + (decision == DispositionDecision.REWORK ? "，待复验项目 " + reworkItemCode : ""),
                actor);
        if (deviation.getStatus() == DeviationStatus.CLOSED) {
            record(batch, EventType.DEVIATION_CLOSED, "偏差 " + deviationNo + " 关闭", actor);
        }
        return deviation;
    }

    // ---------------------------------------------------------------------
    // 双角色审批
    // ---------------------------------------------------------------------

    /** 授予一个角色的放行审批；同一角色在撤回前不能重复审批。 */
    @Transactional
    public ReleaseApproval grantApproval(String batchNo, String roleCode, String approver, String comment) {
        Batch batch = lockBatch(batchNo);
        requireCreated(batch, "批次已放行，审批不可变更");
        boolean exists = batch.getApprovals().stream()
                .anyMatch(a -> a.getRoleCode().equals(roleCode) && !a.isWithdrawn());
        if (exists) {
            throw new BusinessRuleException("角色 " + roleCode + " 已有有效审批，需先撤回");
        }
        ReleaseApproval approval = new ReleaseApproval(batch, roleCode, approver, comment);
        batch.getApprovals().add(approval);
        record(batch, EventType.APPROVAL_GRANTED, "角色 " + roleCode + " 审批通过（" + approver + "）", approver);
        return approval;
    }

    /** 撤回某角色的审批；放行后不允许撤回。 */
    @Transactional
    public ReleaseApproval withdrawApproval(String batchNo, String roleCode, String actor) {
        Batch batch = lockBatch(batchNo);
        requireCreated(batch, "批次已放行，审批不可撤回");
        ReleaseApproval approval = batch.getApprovals().stream()
                .filter(a -> a.getRoleCode().equals(roleCode) && !a.isWithdrawn())
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("角色 " + roleCode + " 没有可撤回的有效审批"));
        approval.withdraw();
        record(batch, EventType.APPROVAL_WITHDRAWN, "角色 " + roleCode + " 撤回审批", actor);
        return approval;
    }

    // ---------------------------------------------------------------------
    // 放行
    // ---------------------------------------------------------------------

    /**
     * 放行批次。放行业务号 {@code releaseNo} 保证幂等：重复提交（含与首次提交并发的重试）
     * 返回同一放行记录，不会产生第二张放行单。
     *
     * <p>放行条件：所有声明的检验项目都有当前有效结果；不合格结果关联的偏差以及批次的全部
     * 偏差都处于允许放行的终态（有条件接受，或返工后复验合格关闭；报废禁止放行）；
     * 至少两个不同角色的当前有效审批。
     *
     * <p>校验、放行快照（锁定使用的检验结果版本与偏差版本）与批次状态翻转在同一事务、
     * 持有批次行锁期间完成。
     */
    @Transactional
    public Release release(String releaseNo, String batchNo, String actor) {
        // 先锁批次行：放行与任何资料补录/审批撤回互斥串行
        Batch batch = lockBatch(batchNo);

        Release existing = releaseRepository.findByReleaseNo(releaseNo).orElse(null);
        if (existing != null) {
            if (!existing.getBatch().getId().equals(batch.getId())) {
                throw new BusinessRuleException(
                        "放行业务号 " + releaseNo + " 已用于其他批次");
            }
            return existing; // 幂等重试
        }
        Release batchRelease = releaseRepository.findByBatchId(batch.getId()).orElse(null);
        if (batchRelease != null) {
            throw new BusinessRuleException(
                    "批次已使用放行业务号 " + batchRelease.getReleaseNo() + " 放行");
        }
        if (batch.getStatus() != BatchStatus.CREATED) {
            throw new BusinessRuleException("批次当前状态 " + batch.getStatus() + "，不能放行");
        }

        List<String> blockers = releaseBlockers(batch);
        if (!blockers.isEmpty()) {
            throw new BusinessRuleException("批次不满足放行条件：" + String.join("；", blockers));
        }

        Release release = new Release(releaseNo, batch, actor);
        release = releaseRepository.save(release);
        releaseRepository.flush(); // 取得放行主键后建立快照

        for (InspectionItem item : batch.getItems()) {
            InspectionResultVersion current = item.currentResult().orElseThrow();
            release.addEvidenceItem(ReleaseEvidenceItem.forResult(release, item, current));
        }
        for (Deviation deviation : batch.getDeviations()) {
            release.addEvidenceItem(ReleaseEvidenceItem.forDeviation(release, deviation));
        }

        batch.setStatus(BatchStatus.RELEASED);
        record(batch, EventType.RELEASED,
                "放行，业务号 " + releaseNo + "，锁定检验结果版本 "
                        + batch.getItems().size() + " 条、偏差版本 " + batch.getDeviations().size() + " 条",
                actor);
        return release;
    }

    /** 计算放行阻碍项；空列表表示可以放行。 */
    private List<String> releaseBlockers(Batch batch) {
        List<String> blockers = new ArrayList<>();

        for (InspectionItem item : batch.getItems()) {
            InspectionResultVersion current = item.currentResult().orElse(null);
            if (current == null) {
                blockers.add("检验项目 " + item.getItemCode() + " 缺少当前有效结果");
                continue;
            }
            if (!current.isConforming()) {
                Deviation d = current.getDeviation();
                if (d == null) {
                    blockers.add("检验项目 " + item.getItemCode() + " 的不合格结果未关联偏差单");
                } else if (d.getStatus() != DeviationStatus.CLOSED) {
                    blockers.add("检验项目 " + item.getItemCode() + " 关联的偏差 "
                            + d.getDeviationNo() + " 尚未终态关闭");
                }
            }
        }

        for (Deviation d : batch.getDeviations()) {
            if (d.getStatus() != DeviationStatus.CLOSED) {
                blockers.add("偏差 " + d.getDeviationNo() + " 未终态关闭（" + d.getStatus() + "）");
                continue;
            }
            if (d.getDecision() == DispositionDecision.SCRAP) {
                blockers.add("偏差 " + d.getDeviationNo() + " 终态为报废，禁止放行");
            } else if (d.getDecision() == DispositionDecision.REWORK) {
                InspectionItem reworkItem = batch.findItem(d.getReworkItemCode()).orElse(null);
                InspectionResultVersion current =
                        reworkItem == null ? null : reworkItem.currentResult().orElse(null);
                if (current == null || !current.isConforming()) {
                    blockers.add("偏差 " + d.getDeviationNo()
                            + " 返工后复验项目 " + d.getReworkItemCode() + " 当前结果不合格");
                }
            }
        }

        long distinctActiveRoles = batch.getApprovals().stream()
                .filter(a -> !a.isWithdrawn())
                .map(ReleaseApproval::getRoleCode)
                .distinct().count();
        if (distinctActiveRoles < 2) {
            blockers.add("需要两个不同角色的有效审批，当前为 " + distinctActiveRoles + " 个");
        }
        return blockers;
    }

    // ---------------------------------------------------------------------
    // 撤销放行 / 出库流转
    // ---------------------------------------------------------------------

    /**
     * 登记撤销放行事件。放行记录与依据不可修改，撤销只追加事件并翻转批次状态；
     * 尚未出库的批次因此被阻止继续流转，已出库批次记录该事实以便追溯。
     */
    @Transactional
    public ReleaseRevocation revokeRelease(String releaseNo, String reason, String actor) {
        Release release = releaseRepository.findByReleaseNo(releaseNo)
                .orElseThrow(() -> new ResourceNotFoundException("放行记录不存在: " + releaseNo));
        Batch batch = lockBatch(release.getBatch().getBatchNo());
        if (revocationRepository.findByReleaseId(release.getId()).isPresent()) {
            throw new BusinessRuleException("放行 " + releaseNo + " 已登记撤销，不能重复撤销");
        }
        boolean alreadyShipped = batch.getStatus() == BatchStatus.SHIPPED;
        ReleaseRevocation revocation = new ReleaseRevocation(release, reason, actor, alreadyShipped);
        revocationRepository.save(revocation);
        batch.setStatus(BatchStatus.REVOKED);
        record(batch, EventType.RELEASE_REVOKED,
                "撤销放行 " + releaseNo + (alreadyShipped ? "（批次已出库）" : "（批次尚未出库，阻止继续流转）")
                        + "，原因：" + reason,
                actor);
        return revocation;
    }

    /** 批次出库流转；仅已放行且未撤销的批次可以出库。 */
    @Transactional
    public void ship(String batchNo, String actor) {
        Batch batch = lockBatch(batchNo);
        switch (batch.getStatus()) {
            case RELEASED -> {
                batch.setStatus(BatchStatus.SHIPPED);
                record(batch, EventType.SHIPPED, "批次出库", actor);
            }
            case REVOKED -> throw new BusinessRuleException("批次放行已撤销，禁止出库流转");
            case SHIPPED -> throw new BusinessRuleException("批次已出库");
            default -> throw new BusinessRuleException("批次尚未放行，不能出库");
        }
    }

    // ---------------------------------------------------------------------
    // 查询：当前结论 / 证据包 / 完整版本历史
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Batch getBatch(String batchNo) {
        Batch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new ResourceNotFoundException("批次不存在: " + batchNo));
        initializeDetails(batch);
        return batch;
    }

    /** 当前结论：状态、能否放行及阻碍项、放行与撤销信息。 */
    @Transactional(readOnly = true)
    public CurrentConclusion currentConclusion(String batchNo) {
        Batch batch = getBatch(batchNo);
        Release release = releaseRepository.findByBatchId(batch.getId()).orElse(null);
        ReleaseRevocation revocation = null;
        if (release != null) {
            release.getBatch().getBatchNo();
            revocation = revocationRepository.findByReleaseId(release.getId()).orElse(null);
            if (revocation != null) {
                revocation.getRelease().getReleaseNo();
            }
        }
        List<String> blockers = batch.getStatus() == BatchStatus.CREATED ? releaseBlockers(batch) : List.of();
        return new CurrentConclusion(batch, blockers, blockers.isEmpty(), release, revocation);
    }

    /** 批次证据包：放行时锁定的检验结果版本与偏差版本快照。 */
    @Transactional(readOnly = true)
    public Release evidencePackage(String releaseNo) {
        Release release = releaseRepository.findByReleaseNo(releaseNo)
                .orElseThrow(() -> new ResourceNotFoundException("放行记录不存在: " + releaseNo));
        release.getEvidenceItems().size();
        release.getBatch().getBatchNo();
        return release;
    }

    /** 完整版本历史（事务内读取全部关联，供视图层组装）。 */
    @Transactional(readOnly = true)
    public Batch history(String batchNo) {
        return getBatch(batchNo);
    }

    // ---------------------------------------------------------------------

    private void initializeDetails(Batch batch) {
        batch.getItems().forEach(i -> i.getResults().forEach(r -> {
            if (r.getDeviation() != null) {
                r.getDeviation().getDeviationNo();
            }
        }));
        batch.getDeviations().size();
        batch.getApprovals().size();
        batch.getEvents().size();
    }

    private Batch lockBatch(String batchNo) {
        return batchRepository.findByBatchNoForUpdate(batchNo)
                .orElseThrow(() -> new ResourceNotFoundException("批次不存在: " + batchNo));
    }

    private void requireCreated(Batch batch, String message) {
        if (batch.getStatus() != BatchStatus.CREATED) {
            throw new BusinessRuleException(message + "，当前状态：" + batch.getStatus());
        }
    }

    private void record(Batch batch, EventType type, String detail, String actor) {
        batch.getEvents().add(new BatchEvent(batch, type, detail, actor));
    }

    /** 声明检验项目的输入值对象。 */
    public record ItemDeclaration(String itemCode, String itemName, String specification) {
    }

    /** 批次当前结论视图载体。 */
    public record CurrentConclusion(Batch batch, List<String> blockers, boolean releasable,
                                    Release release, ReleaseRevocation revocation) {
    }
}
