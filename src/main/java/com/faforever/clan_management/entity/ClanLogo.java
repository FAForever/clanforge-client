package com.faforever.clan_management.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clan_logo")
public class ClanLogo {

    @Id
    @Column(name = "clan_id")
    private Long clanId;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type",nullable = false)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ClanLogo() {
    }

    public ClanLogo(Long clanId, byte[] content, String contentType) {
        this.clanId = clanId;
        this.content = content;
        this.contentType = contentType;
        this.updatedAt = OffsetDateTime.now();
    }

    public void replace(byte[] content, String contentType) {
        this.content = content;
        this.contentType = contentType;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getClanId() {
        return clanId;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
