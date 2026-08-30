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

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getVisibility() {
        return visibility;
    }

    public boolean isRequiresInvitation() {
        return requiresInvitation;
    }

    public String getStatus() {
        return status;
    }

    public String getTagColor() {
        return tagColor;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
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
}