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
 * 不合格结果关联的偏差单。
 *
 * <p>处置决定为返工、报废或有条件接受：
 * <ul>
 *     <li>有条件接受：直接关闭，允许放行；</li>
 *     <li>报废：直接关闭，批次永久不可放行；</li>
 *     <li>返工：进入处置中，关联检验项目复验合格后才能关闭；复验仍不合格则批次不可放行。</li>
 * </ul>
 * 未关闭的偏差一律阻止放行。
 */
@Entity
@Table(name = "deviation")
public class Deviation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    /** 偏差单号，全局唯一。 */
    @Column(nullable = false, unique = true)
    private String deviationNo;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviationStatus status = DeviationStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DispositionDecision decision;

    @Column(length = 2000)
    private String dispositionRemark;

    /** 返工偏差等待复验合格的检验项目。 */
    @Column(name = "rework_item_code")
    private String reworkItemCode;

    private String dispositionBy;

    private Instant dispositionedAt;

    private Instant closedAt;

    @Column(nullable = false)
    private Instant openedAt = Instant.now();

    protected Deviation() {
    }

    public Deviation(Batch batch, String deviationNo, String description) {
        this.batch = batch;
        this.deviationNo = deviationNo;
        this.description = description;
    }

    /** 登记处置决定。返工进入处置中并记录待复验项目；报废/有条件接受直接关闭。 */
    public void applyDisposition(DispositionDecision decision, String remark, String operator) {
        this.decision = decision;
        this.dispositionRemark = remark;
        this.dispositionBy = operator;
        this.dispositionedAt = Instant.now();
        if (decision == DispositionDecision.REWORK) {
            this.status = DeviationStatus.IN_DISPOSITION;
        } else {
            this.status = DeviationStatus.CLOSED;
            this.closedAt = this.dispositionedAt;
        }
    }

    /** 返工复验合格：项目代码匹配时关闭偏差。 */
    public void reworkVerified(String itemCode) {
        if (status != DeviationStatus.IN_DISPOSITION || decision != DispositionDecision.REWORK) {
            throw new IllegalStateException("偏差 " + deviationNo + " 不处于待复验关闭状态");
        }
        if (!itemCode.equals(reworkItemCode)) {
            throw new IllegalStateException("复验项目 " + itemCode + " 与返工项目 " + reworkItemCode + " 不一致");
        }
        this.status = DeviationStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public void setReworkItemCode(String reworkItemCode) {
        this.reworkItemCode = reworkItemCode;
    }

    public Long getId() {
        return id;
    }

    public Batch getBatch() {
        return batch;
    }

    public String getDeviationNo() {
        return deviationNo;
    }

    public String getDescription() {
        return description;
    }

    public DeviationStatus getStatus() {
        return status;
    }

    public DispositionDecision getDecision() {
        return decision;
    }

    public String getDispositionRemark() {
        return dispositionRemark;
    }

    public String getReworkItemCode() {
        return reworkItemCode;
    }

    public String getDispositionBy() {
        return dispositionBy;
    }

    public Instant getDispositionedAt() {
        return dispositionedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }
}
