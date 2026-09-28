package com.chris64233.manufacturingrelease.domain;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 批次放行记录。创建即代表批次进入已放行终态。
 *
 * <p>放行业务号 {@code releaseNo} 全局唯一，保证幂等；创建时在同一事务内生成
 * {@link ReleaseEvidenceItem 依据快照}，把当时使用的检验结果版本与偏差版本永久锁定，
 * 此后检验结果的新增版本、偏差的后续处理都不影响本次放行。
 */
@Entity
@Table(name = "release_record")
public class Release {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 放行业务号，全局唯一（幂等键）。 */
    @Column(nullable = false, unique = true)
    private String releaseNo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, unique = true)
    private Batch batch;

    @Column(nullable = false)
    private String releasedBy;

    @Column(nullable = false)
    private Instant releasedAt = Instant.now();

    @OneToMany(mappedBy = "release", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReleaseEvidenceItem> evidenceItems = new ArrayList<>();

    protected Release() {
    }

    public Release(String releaseNo, Batch batch, String releasedBy) {
        this.releaseNo = releaseNo;
        this.batch = batch;
        this.releasedBy = releasedBy;
    }

    public void addEvidenceItem(ReleaseEvidenceItem item) {
        evidenceItems.add(item);
    }

    public Long getId() {
        return id;
    }

    public String getReleaseNo() {
        return releaseNo;
    }

    public Batch getBatch() {
        return batch;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public List<ReleaseEvidenceItem> getEvidenceItems() {
        return evidenceItems;
    }
}
