package com.faforever.clan_management.api;

import com.faforever.clan_management.service.ClanForm;

public record ClanWriteRequest(
        String name,
        String tag,
        String description,
        String region,
        Boolean publicClan,
        Boolean requiresInvitation
) {
    public ClanForm toForm() {
        ClanForm form = new ClanForm();
        form.setName(name);
        form.setTag(tag);
        form.setDescription(description);
        form.setRegion(region);
        form.setPublicClan(publicClan == null || publicClan);
        form.setRequiresInvitation(
                requiresInvitation != null && requiresInvitation
        );
        return form;
    }
}
