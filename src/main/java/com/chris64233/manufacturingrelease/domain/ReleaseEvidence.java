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

/**
 * 放行依据快照：放行事务中对“本次使用的检验结果版本、偏差终态处理、有效审批”
 * 逐行留痕并锁定。即使之后检验结果被新版本替代、审批被撤回或偏差记录演进，
 * 已放行批次的依据仍以本快照为准、完整可查。
 */
@Entity
@Table(name = "release_evidence")
public class ReleaseEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false)
    private BatchRelease release;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EvidenceType evidenceType;

    /** 来源行主键（inspection_result / deviation / release_approval 的 id） */
    @Column(nullable = false)
    private Long sourceId;

    /** 来源业务号：检验项目编码 / 偏差单号 / 审批角色 */
    @Column(nullable = false, length = 64)
    private String sourceKey;

    /** 锁定的版本号（检验结果为其版本号，其余为 1） */
    @Column(nullable = false)
    private int lockedVersion;

    /** 证据内容摘要（判定/处置/审批人等） */
    @Column(nullable = false, length = 1000)
    private String summary;

    protected ReleaseEvidence() {
    }

    public ReleaseEvidence(BatchRelease release, EvidenceType evidenceType, Long sourceId, String sourceKey,
                           int lockedVersion, String summary) {
        this.release = release;
        this.evidenceType = evidenceType;
        this.sourceId = sourceId;
        this.sourceKey = sourceKey;
        this.lockedVersion = lockedVersion;
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public BatchRelease getRelease() {
        return release;
    }

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public int getLockedVersion() {
        return lockedVersion;
    }

    public String getSummary() {
        return summary;
    }
}
