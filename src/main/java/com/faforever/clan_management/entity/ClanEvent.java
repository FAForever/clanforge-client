package com.faforever.clan_management.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clan_event")
public class ClanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    //bound as json rather than varchar, postgres refuses a plain string into a jsonb column
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @ManyToOne
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "last_error")
    private String lastError;

    protected ClanEvent() {
    }

    public ClanEvent(String entityType, Long entityId, String eventType, String payload, AppUser actor) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventType = eventType;
        this.payload = payload;
        this.actor = actor;
        this.occurredAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public AppUser getActor() {
        return actor;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public String getLastError() {
        return lastError;
    }
}
