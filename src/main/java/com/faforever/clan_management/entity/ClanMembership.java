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
@Table(name = "clan_membership")
public class ClanMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "clan_id", nullable = false)
    private Clan clan;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private String role;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(nullable = false)
    private String source = "local";

    @Column(name = "source_id")
    private String sourceId;

    protected ClanMembership() {
    }

    public ClanMembership(Clan clan, AppUser user, String role) {
        this.clan = clan;
        this.user = user;
        this.role = role;
        this.joinedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Clan getClan() {
        return clan;
    }

    public AppUser getUser() {
        return user;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getSourceId() {
        return sourceId;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(OffsetDateTime leftAt) {
        this.leftAt = leftAt;
    }

    public String getSource() {
        return source;
    }
}
