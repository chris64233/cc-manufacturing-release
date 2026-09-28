package com.chris64233.manufacturingrelease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 放行依据快照项：放行事务内对"当时使用的每个检验结果版本 / 每个偏差版本"的不可变拷贝。
 *
 * <p>记录结果行与偏差行的主键（具体行版本）及其结论，使得放行后任何补录、替代、
 * 处置变更都无法改变本次放行所依据的内容。
 */
@Entity
@Table(name = "release_evidence_item")
public class ReleaseEvidenceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false)
    private Release release;

    /** RESULT（检验结果版本）或 DEVIATION（偏差版本）。 */
    @Column(nullable = false, length = 16)
    private String evidenceType;

    private String itemCode;

    /** 被锁定的检验结果版本行 ID。 */
    @Column(name = "result_version_id")
    private Long resultVersionId;

    private Integer resultVersionNo;

    private Boolean conforming;

    private String valueText;

    /** 被锁定的偏差行 ID。 */
    @Column(name = "deviation_id")
    private Long deviationId;

    private String deviationNo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeviationStatus deviationStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DispositionDecision deviationDecision;

    private String submittedBy;

    private Instant submittedAt;

    protected ReleaseEvidenceItem() {
    }

    /** 检验结果版本快照。 */
    public static ReleaseEvidenceItem forResult(Release release, InspectionItem item,
                                                InspectionResultVersion result) {
        ReleaseEvidenceItem e = new ReleaseEvidenceItem();
        e.release = release;
        e.evidenceType = "RESULT";
        e.itemCode = item.getItemCode();
        e.resultVersionId = result.getId();
        e.resultVersionNo = result.getVersionNo();
        e.conforming = result.isConforming();
        e.valueText = result.getValueText();
        e.submittedBy = result.getSubmittedBy();
        e.submittedAt = result.getSubmittedAt();
        return e;
    }

    /** 偏差版本快照。 */
    public static ReleaseEvidenceItem forDeviation(Release release, Deviation deviation) {
        ReleaseEvidenceItem e = new ReleaseEvidenceItem();
        e.release = release;
        e.evidenceType = "DEVIATION";
        e.deviationId = deviation.getId();
        e.deviationNo = deviation.getDeviationNo();
        e.deviationStatus = deviation.getStatus();
        e.deviationDecision = deviation.getDecision();
        e.submittedBy = deviation.getDispositionBy();
        e.submittedAt = deviation.getDispositionedAt();
        return e;
    }

    public Long getId() {
        return id;
    }

    public Release getRelease() {
        return release;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public String getItemCode() {
        return itemCode;
    }

    public Long getResultVersionId() {
        return resultVersionId;
    }

    public Integer getResultVersionNo() {
        return resultVersionNo;
    }

    public Boolean getConforming() {
        return conforming;
    }

    public String getValueText() {
        return valueText;
    }

    public Long getDeviationId() {
        return deviationId;
    }

    public String getDeviationNo() {
        return deviationNo;
    }

    public DeviationStatus getDeviationStatus() {
        return deviationStatus;
    }

    public DispositionDecision getDeviationDecision() {
        return deviationDecision;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
