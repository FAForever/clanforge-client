package com.faforever.clan_management.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "clan")
public class Clan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String tag;

    private String description;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String visibility = "PUBLIC";

    @Column(name = "requires_invitation", nullable = false)
    private boolean requiresInvitation = false;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "tag_color")
    private String tagColor;

    @Column(name = "website_url")
    private String websiteUrl;

    private String region;

    private String motd;

    @ManyToOne
    @JoinColumn(name = "motd_author_id")
    private AppUser motdAuthor;

    @Column(name = "motd_updated_at")
    private OffsetDateTime motdUpdatedAt;

    @Column(name = "logo_updated_at")
    private OffsetDateTime logoUpdatedAt;

    @Column(nullable = false)
    private String source = "local";

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected Clan() {
    }

    public Clan(String name, String tag, String description, AppUser owner, String visibility, boolean requiresInvitation) {
        this.name = name;
        this.tag = tag;
        this.description = description;
        this.owner = owner;
        this.visibility = visibility;
        this.requiresInvitation = requiresInvitation;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AppUser getOwner() {
        return owner;
    }

    public void setOwner(AppUser owner) {
        this.owner = owner;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public boolean isRequiresInvitation() {
        return requiresInvitation;
    }

    public void setRequiresInvitation(boolean requiresInvitation) {
        this.requiresInvitation = requiresInvitation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTagColor() {
        return tagColor;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getMotd() {
        return motd;
    }

    public AppUser getMotdAuthor() {
        return motdAuthor;
    }

    public OffsetDateTime getMotdUpdatedAt() {
        return motdUpdatedAt;
    }

    public void setMotd(String motd, AppUser author) {
        this.motd = motd;
        this.motdAuthor = motd == null ? null : author;
        this.motdUpdatedAt = motd == null ? null : OffsetDateTime.now();
    }

    public OffsetDateTime getLogoUpdatedAt() {
        return logoUpdatedAt;
    }

    public void setLogoUpdatedAt(OffsetDateTime logoUpdatedAt) {
        this.logoUpdatedAt = logoUpdatedAt;
    }

    public boolean isHasLogo() {
        return logoUpdatedAt != null;
    }

    public String getSource() {
        return source;
    }

    public String getSourceId() {
        return sourceId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
