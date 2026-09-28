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

/**
 * 质量审批记录。每次授权产生一行；撤回只将记录置为 inactive（保留历史）。
 * 放行要求同一批次存在两个不同 {@link ApprovalRole} 的有效（active）审批。
 */
@Entity
@Table(name = "release_approval")
public class ReleaseApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalRole role;

    @Column(nullable = false, length = 64)
    private String approver;

    @Column(length = 1000)
    private String comment;

    /** 是否仍然有效；撤回后置为 false */
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    private Instant withdrawnAt;

    protected ReleaseApproval() {
    }

    public ReleaseApproval(ProductionBatch batch, ApprovalRole role, String approver, String comment) {
        this.batch = batch;
        this.role = role;
        this.approver = approver;
        this.comment = comment;
    }

    public void withdraw() {
        if (!active) {
            throw new IllegalStateException("审批已撤回，不能重复撤回");
        }
        this.active = false;
        this.withdrawnAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public ApprovalRole getRole() {
        return role;
    }

    public String getApprover() {
        return approver;
    }

    public String getComment() {
        return comment;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
