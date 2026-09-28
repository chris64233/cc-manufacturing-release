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
 * 放行审批。最终放行需要两个<strong>不同角色</strong>的当前有效审批；
 * 放行前可以撤回（{@code withdrawn=true}），撤回的审批不参与放行判定。
 *
 * <p>同一批次同一角色只允许有一条当前有效（未撤回）审批；该约束由持有批次行锁的
 * 领域服务保证，撤回后可再次审批并完整保留历史。
 */
@Entity
@Table(name = "release_approval")
public class ReleaseApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    /** 审批角色，例如 QA_MANAGER、PRODUCTION_MANAGER。 */
    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(nullable = false)
    private String approver;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private boolean withdrawn = false;

    @Column(nullable = false)
    private Instant grantedAt = Instant.now();

    private Instant withdrawnAt;

    protected ReleaseApproval() {
    }

    public ReleaseApproval(Batch batch, String roleCode, String approver, String comment) {
        this.batch = batch;
        this.roleCode = roleCode;
        this.approver = approver;
        this.comment = comment;
    }

    public void withdraw() {
        this.withdrawn = true;
        this.withdrawnAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Batch getBatch() {
        return batch;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getApprover() {
        return approver;
    }

    public String getComment() {
        return comment;
    }

    public boolean isWithdrawn() {
        return withdrawn;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
