package com.chris64233.manufacturingrelease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 撤销放行事件。放行后发现错误时只能登记该事件（放行记录与依据不可删除、不可修改）。
 *
 * <p>撤销后批次进入 {@link BatchStatus#REVOKED}：尚未出库的批次被阻止继续流转；
 * 已出库批次同样登记撤销以便追溯，但实物需另行处置。
 */
@Entity
@Table(name = "release_revocation")
public class ReleaseRevocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false, unique = true)
    private Release release;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(nullable = false)
    private String revokedBy;

    /** 撤销时批次是否已经出库（仅记录事实，不改变撤销的登记）。 */
    @Column(nullable = false)
    private boolean alreadyShipped;

    @Column(nullable = false)
    private Instant revokedAt = Instant.now();

    protected ReleaseRevocation() {
    }

    public ReleaseRevocation(Release release, String reason, String revokedBy, boolean alreadyShipped) {
        this.release = release;
        this.reason = reason;
        this.revokedBy = revokedBy;
        this.alreadyShipped = alreadyShipped;
    }

    public Long getId() {
        return id;
    }

    public Release getRelease() {
        return release;
    }

    public String getReason() {
        return reason;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public boolean isAlreadyShipped() {
        return alreadyShipped;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
