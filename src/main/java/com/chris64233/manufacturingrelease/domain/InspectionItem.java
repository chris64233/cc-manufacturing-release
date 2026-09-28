package com.chris64233.manufacturingrelease.domain;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 批次声明的所需检验项目。
 */
@Entity
@Table(name = "inspection_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inspection_item_batch_code", columnNames = {"batch_id", "item_code"})
})
public class InspectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    /** 检验项目编码（同一批次内唯一） */
    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(nullable = false, length = 200)
    private String itemName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionResult> results = new ArrayList<>();

    protected InspectionItem() {
    }

    public InspectionItem(ProductionBatch batch, String itemCode, String itemName) {
        this.batch = batch;
        this.itemCode = itemCode;
        this.itemName = itemName;
    }

    public void addResult(InspectionResult result) {
        results.add(result);
    }

    public Long getId() {
        return id;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InspectionResult> getResults() {
        return results;
    }
}
