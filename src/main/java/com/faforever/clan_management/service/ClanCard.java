package com.faforever.clan_management.service;

import com.faforever.clan_management.entity.Clan;

/**
 * A clan plus the derived figures the browse page shows.
 *
 * Kept separate from the entity so the view has what it needs without the
 * entity growing presentation concerns or lazy associations being touched
 * during rendering.
 */
public class ClanCard {

    private final Clan clan;
    private final long memberCount;

    public ClanCard(Clan clan, long memberCount) {
        this.clan = clan;
        this.memberCount = memberCount;
    }

    public Clan getClan() {
        return clan;
    }

    public long getMemberCount() {
        return memberCount;
    }
}
