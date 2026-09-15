package com.faforever.clan_management.service;

import java.time.OffsetDateTime;

//one line in the bell: what happened, when, and where to go
public class Notice {

    private final String text;
    private final OffsetDateTime occurredAt;
    private final String link;

    public Notice(String text, OffsetDateTime occurredAt, String link) {
        this.text = text;
        this.occurredAt = occurredAt;
        this.link = link;
    }

    public String getText() {
        return text;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getLink() {
        return link;
    }

    public String getWhen() {
        return Names.ago(occurredAt);
    }
}
