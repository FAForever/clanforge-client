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
@Table(name = "clan_invitation")
public class ClanInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne
    @JoinColumn(name = "invitee_id", nullable = false)
    private AppUser invitee;

    @ManyToOne
    @JoinColumn(name = "inviter_id", nullable = false)
    private AppUser inviter;

    @Column(nullable = false)
    private String state = "PENDING";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    protected ClanInvitation() {
    }

    public ClanInvitation(Clan clan, AppUser invitee, AppUser inviter, OffsetDateTime expiresAt) {
        this.clan = clan;
        this.invitee = invitee;
        this.inviter = inviter;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public AppUser getInvitee() {
        return invitee;
    }

    public AppUser getInviter() {
        return inviter;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(OffsetDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }
}
