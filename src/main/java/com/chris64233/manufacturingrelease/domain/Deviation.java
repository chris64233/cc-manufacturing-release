package com.chris64233.manufacturingrelease.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 偏差单：由不合格检验结果关联触发，可决定返工、报废或有条件接受。
 * 只有处于终态（DECIDED）且处置结论允许放行的偏差不阻塞批次放行。
 */
@Entity
@Table(name = "deviation")
public class Deviation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 偏差单业务号 */
    @Column(nullable = false, unique = true, length = 64)
    private String deviationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviationStatus status = DeviationStatus.OPEN;

    /** 终态处置方式，未决定时为空 */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Disposition disposition;

    @Column(length = 1000)
    private String decisionComment;

    @Column(length = 64)
    private String decidedBy;

    private Instant decidedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** 关联到本偏差单的（当前或历史）不合格结果 */
    @OneToMany(mappedBy = "deviation")
    private List<InspectionResult> relatedResults = new ArrayList<>();

    protected Deviation() {
    }

    public Deviation(String deviationNo, ProductionBatch batch, String description) {
        this.deviationNo = deviationNo;
        this.batch = batch;
        this.description = description;
    }

    public void decide(Disposition disposition, String decisionComment, String decidedBy) {
        if (this.status == DeviationStatus.DECIDED) {
            throw new IllegalStateException("偏差单已作出终态处理，不可更改: " + deviationNo);
        }
        this.status = DeviationStatus.DECIDED;
        this.disposition = disposition;
        this.decisionComment = decisionComment;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
    }

    /** 终态处理且处置结论允许放行 */
    public boolean isReleasePermitting() {
        return status == DeviationStatus.DECIDED
                && disposition != null
                && disposition.allowsRelease();
    }

    public Long getId() {
        return id;
    }

    public String getDeviationNo() {
        return deviationNo;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public String getDescription() {
        return description;
    }

    public DeviationStatus getStatus() {
        return status;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InspectionResult> getRelatedResults() {
        return relatedResults;
    }
}
