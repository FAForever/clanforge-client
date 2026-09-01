package com.faforever.clan_management.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clan_report")
public class ClanReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne
    @JoinColumn(name = "reporter_id", nullable = false)
    private AppUser reporter;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String state = "OPEN";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @ManyToOne
    @JoinColumn(name = "resolved_by")
    private AppUser resolvedBy;

    protected ClanReport() {
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public AppUser getReporter() {
        return reporter;
    }

    public String getCategory() {
        return category;
    }

    public String getReason() {
        return reason;
    }

    public String getState() {
        return state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public AppUser getResolvedBy() {
        return resolvedBy;
    }
}