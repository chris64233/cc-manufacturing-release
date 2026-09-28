package com.chris64233.manufacturingrelease.domain;

import java.time.Instant;

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
import jakarta.persistence.UniqueConstraint;

/**
 * 检验结果（带版本）。同一检验项目可多次提交结果，新版本会将旧版本标记为
 * {@code current=false}；被替代的历史结果保留但永远不能用于放行。
 * 不合格结果必须关联偏差单。
 */
@Entity
@Table(name = "inspection_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inspection_result_item_version", columnNames = {"item_id", "version"})
})
public class InspectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InspectionItem item;

    /** 项目内单调递增的版本号，从 1 开始 */
    @Column(nullable = false)
    private int version;

    /** 检验值/结论描述 */
    @Column(nullable = false, length = 1000)
    private String resultValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Conformity conformity;

    /** 不合格结果关联的偏差单；合格结果为空 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deviation_id")
    private Deviation deviation;

    /** 是否为当前有效版本（同一项目至多一个 true） */
    @Column(nullable = false)
    private boolean current = true;

    @Column(nullable = false, length = 64)
    private String submittedBy;

    @Column(nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    protected InspectionResult() {
    }

    public InspectionResult(InspectionItem item, int version, String resultValue, Conformity conformity,
                            Deviation deviation, String submittedBy) {
        this.item = item;
        this.version = version;
        this.resultValue = resultValue;
        this.conformity = conformity;
        this.deviation = deviation;
        this.submittedBy = submittedBy;
    }

    public void markSuperseded() {
        this.current = false;
    }

    public Long getId() {
        return id;
    }

    public InspectionItem getItem() {
        return item;
    }

    public int getVersion() {
        return version;
    }

    public String getResultValue() {
        return resultValue;
    }

    public Conformity getConformity() {
        return conformity;
    }

    public Deviation getDeviation() {
        return deviation;
    }

    public boolean isCurrent() {
        return current;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
