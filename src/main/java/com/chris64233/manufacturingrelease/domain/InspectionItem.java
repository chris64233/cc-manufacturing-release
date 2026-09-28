package com.chris64233.manufacturingrelease.domain;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 批次所需的检验项目。检验结果以带版本的方式提交，旧版本保留历史但不能用于放行。
 */
@Entity
@Table(name = "inspection_item")
public class InspectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    /** 项目代码，同一批次内唯一。 */
    @Column(nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String itemName;

    /** 规格/判定标准说明。 */
    private String specification;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNo ASC")
    private List<InspectionResultVersion> results = new ArrayList<>();

    protected InspectionItem() {
    }

    public InspectionItem(String itemCode, String itemName, String specification) {
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.specification = specification;
    }

    void bindBatch(Batch batch) {
        this.batch = batch;
    }

    /** 追加一版检验结果，并把同一项目的上一版标记为已替代；返回新版本。 */
    public InspectionResultVersion submitResult(boolean conforming, String valueText, Deviation deviation,
                                                String submittedBy) {
        results.forEach(InspectionResultVersion::markSuperseded);
        InspectionResultVersion version = new InspectionResultVersion(this, results.size() + 1,
                conforming, valueText, deviation, submittedBy);
        results.add(version);
        return version;
    }

    /** 当前有效结果（未被后续版本替代）。 */
    public Optional<InspectionResultVersion> currentResult() {
        return results.stream().filter(r -> !r.isSuperseded()).findFirst();
    }

    public Long getId() {
        return id;
    }

    public Batch getBatch() {
        return batch;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public String getSpecification() {
        return specification;
    }

    public List<InspectionResultVersion> getResults() {
        return results;
    }
}
