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
@Table(name = "moderation_action")
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne
    @JoinColumn(name = "moderator_id", nullable = false)
    private AppUser moderator;

    @Column(nullable = false)
    private String action;

    private String reason;

    @ManyToOne
    @JoinColumn(name = "report_id")
    private ClanReport report;

    //bound as json rather than varchar, postgres refuses a plain string into a jsonb column
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String previous;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    protected ModerationAction() {
    }

    public ModerationAction(Clan clan, AppUser moderator, String action, String reason, ClanReport report, String previous) {
        this.clan = clan;
        this.moderator = moderator;
        this.action = action;
        this.reason = reason;
        this.report = report;
        this.previous = previous;
        this.performedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public AppUser getModerator() {
        return moderator;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public ClanReport getReport() {
        return report;
    }

    public String getPrevious() {
        return previous;
    }

    public OffsetDateTime getPerformedAt() {
        return performedAt;
    }
}
