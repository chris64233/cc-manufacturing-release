package com.chris64233.manufacturingrelease.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 生产批次：声明产品、数量和所需检验项目，是全部质量资料的聚合根。
 *
 * <p>放行后数量与放行依据不可修改；发现错误只能登记撤销放行事件。
 */
@Entity
@Table(name = "batch")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 批次业务号，全局唯一。 */
    @Column(nullable = false, unique = true)
    private String batchNo;

    @Column(nullable = false)
    private String productCode;

    private String productName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchStatus status = BatchStatus.CREATED;

    /** JPA 乐观版本号；配合行级悲观写锁使用。 */
    @Version
    private long lockVersion;

    @Column(nullable = false)
    private Instant declaredAt = Instant.now();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Deviation> deviations = new ArrayList<>();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReleaseApproval> approvals = new ArrayList<>();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC, id ASC")
    private List<BatchEvent> events = new ArrayList<>();

    protected Batch() {
    }

    public Batch(String batchNo, String productCode, String productName, BigDecimal quantity) {
        this.batchNo = batchNo;
        this.productCode = productCode;
        this.productName = productName;
        this.quantity = quantity;
    }

    public void addItem(InspectionItem item) {
        items.add(item);
        item.bindBatch(this);
    }

    public Optional<InspectionItem> findItem(String itemCode) {
        return items.stream().filter(i -> i.getItemCode().equals(itemCode)).findFirst();
    }

    public boolean isReleasedOrBeyond() {
        return status != BatchStatus.CREATED;
    }

    public boolean isMutable() {
        return status == BatchStatus.CREATED;
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

    public String getProductName() {
        return productName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public long getLockVersion() {
        return lockVersion;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public List<InspectionItem> getItems() {
        return items;
    }

    public List<Deviation> getDeviations() {
        return deviations;
    }

    public List<ReleaseApproval> getApprovals() {
        return approvals;
    }

    public List<BatchEvent> getEvents() {
        return events;
    }
}
