package com.faforever.clan_management.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clan_calendar_event")
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @Column(nullable = false)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CalendarEvent() {
    }

    public CalendarEvent(Clan clan, String title,OffsetDateTime startsAt,AppUser createdBy) {
        this.clan = clan;
        this.title = title;
        this.startsAt = startsAt;
        this.createdBy = createdBy;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public String getTitle() {
        return title;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getStartsAtUtc() {
        return startsAt.withOffsetSameInstant(ZoneOffset.UTC); //the form takes utc, so the page shows utc whatever zone the server runs in
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
