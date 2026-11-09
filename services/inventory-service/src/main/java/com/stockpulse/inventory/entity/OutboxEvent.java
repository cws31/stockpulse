package com.stockpulse.inventory.entity;

import com.stockpulse.inventory.domain.OutboxStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events",
       indexes = @Index(name = "idx_outbox_status_created", columnList = "status, created_at"))
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateId, String eventType, String correlationId, String payload) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = 1;
        this.correlationId = correlationId;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getCorrelationId() { return correlationId; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
    }
}
