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
 * 批次审计事件：所有关键业务动作的追加式记录，提供完整版本历史。
 */
@Entity
@Table(name = "batch_event")
public class BatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EventType type;

    @Column(length = 2000)
    private String detail;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    protected BatchEvent() {
    }

    public BatchEvent(Batch batch, EventType type, String detail, String actor) {
        this.batch = batch;
        this.type = type;
        this.detail = detail;
        this.actor = actor;
    }

    public Long getId() {
        return id;
    }

    public EventType getType() {
        return type;
    }

    public String getDetail() {
        return detail;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
