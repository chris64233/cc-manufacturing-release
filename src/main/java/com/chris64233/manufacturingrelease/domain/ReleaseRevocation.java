package com.chris64233.manufacturingrelease.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 撤销放行事件。放行发现错误时不能修改或删除原放行，只能登记撤销事件。
 * 撤销后批次进入 {@link BatchStatus#RELEASE_REVOKED}，阻止尚未出库的批次继续流转。
 */
@Entity
@Table(name = "release_revocation")
public class ReleaseRevocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false)
    private BatchRelease release;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 64)
    private String revokedBy;

    @Column(nullable = false, updatable = false)
    private Instant revokedAt = Instant.now();

    protected ReleaseRevocation() {
    }

    public ReleaseRevocation(BatchRelease release, String reason, String revokedBy) {
        this.release = release;
        this.reason = reason;
        this.revokedBy = revokedBy;
    }

    public Long getId() {
        return id;
    }

    public BatchRelease getRelease() {
        return release;
    }

    public String getReason() {
        return reason;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
