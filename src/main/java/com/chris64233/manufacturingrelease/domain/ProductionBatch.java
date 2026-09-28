package com.chris64233.manufacturingrelease.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 生产批次：声明产品、数量和所需检验项目。
 */
@Entity
@Table(name = "production_batch")
public class ProductionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 批次业务号 */
    @Column(nullable = false, unique = true, length = 64)
    private String batchNo;

    /** 产品标识 */
    @Column(nullable = false, length = 128)
    private String productCode;

    /** 申报数量（批次数量在放行后不可修改） */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BatchStatus status = BatchStatus.PENDING_RELEASE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionItem> inspectionItems = new ArrayList<>();

    protected ProductionBatch() {
    }

    public ProductionBatch(String batchNo, String productCode, BigDecimal quantity) {
        this.batchNo = batchNo;
        this.productCode = productCode;
        this.quantity = quantity;
    }

    public void addItem(InspectionItem item) {
        inspectionItems.add(item);
    }

    public Long getId() {
        return id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getProductCode() {
        return productCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InspectionItem> getInspectionItems() {
        return inspectionItems;
    }
}
