package com.chris64233.manufacturingrelease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 检验结果版本。
 *
 * <p>每个检验项目可多次提交结果；被后续版本替代（{@code superseded=true}）的结果保留历史，
 * 但绝不能用于放行判定与放行快照。不合格结果必须关联一张偏差单。
 */
@Entity
@Table(name = "inspection_result_version")
public class InspectionResultVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InspectionItem item;

    /** 项目内从 1 递增的版本号。 */
    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private boolean conforming;

    @Column(length = 1000)
    private String valueText;

    /** 不合格结果关联的偏差单；合格结果为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deviation_id")
    private Deviation deviation;

    /** 是否被后续版本替代。 */
    @Column(nullable = false)
    private boolean superseded = false;

    @Column(nullable = false)
    private String submittedBy;

    @Column(nullable = false)
    private Instant submittedAt = Instant.now();

    protected InspectionResultVersion() {
    }

    InspectionResultVersion(InspectionItem item, int versionNo, boolean conforming, String valueText,
                            Deviation deviation, String submittedBy) {
        this.item = item;
        this.versionNo = versionNo;
        this.conforming = conforming;
        this.valueText = valueText;
        this.deviation = deviation;
        this.submittedBy = submittedBy;
    }

    void markSuperseded() {
        this.superseded = true;
    }

    public Long getId() {
        return id;
    }

    public InspectionItem getItem() {
        return item;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public boolean isConforming() {
        return conforming;
    }

    public String getValueText() {
        return valueText;
    }

    public Deviation getDeviation() {
        return deviation;
    }

    public boolean isSuperseded() {
        return superseded;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
