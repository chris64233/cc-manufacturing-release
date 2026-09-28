package com.chris64233.manufacturingrelease.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * 批次放行记录。
 *
 * <p>一次放行在同一事务内：校验放行条件、锁定本次使用的检验结果版本/偏差终态/审批
 * （见 {@link ReleaseEvidence} 快照），并将批次置为已放行。放行业务号（releaseNo）
 * 唯一约束保证幂等：同一业务号重复提交返回首次放行结果，不会二次放行。</p>
 */
@Entity
@Table(name = "batch_release")
public class BatchRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 放行业务号：幂等键，全局唯一 */
    @Column(nullable = false, unique = true, length = 64)
    private String releaseNo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, unique = true)
    private ProductionBatch batch;

    /** 放行时刻锁定的产品与数量（放行后批次数量不可修改） */
    @Column(nullable = false, length = 128)
    private String productCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, length = 64)
    private String releasedBy;

    @Column(nullable = false, updatable = false)
    private Instant releasedAt = Instant.now();

    @OneToMany(mappedBy = "release", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReleaseEvidence> evidences = new ArrayList<>();

    protected BatchRelease() {
    }

    public BatchRelease(String releaseNo, ProductionBatch batch, String releasedBy) {
        this.releaseNo = releaseNo;
        this.batch = batch;
        this.productCode = batch.getProductCode();
        this.quantity = batch.getQuantity();
        this.releasedBy = releasedBy;
    }

    public void addEvidence(ReleaseEvidence evidence) {
        evidences.add(evidence);
    }

    public Long getId() {
        return id;
    }

    public String getReleaseNo() {
        return releaseNo;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public String getProductCode() {
        return productCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public List<ReleaseEvidence> getEvidences() {
        return evidences;
    }
}
