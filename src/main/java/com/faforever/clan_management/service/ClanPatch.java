package com.faforever.clan_management.service;

public record ClanPatch(
        String name,
        String tag,
        String description,
        String region,
        Boolean publicClan,
        Boolean requiresInvitation
) {
    public void applyTo(ClanForm form) {
        if (name != null) form.setName(name);
        if (tag != null) form.setTag(tag);
        if (description != null) form.setDescription(description);
        if (region != null) form.setRegion(region);
        if (publicClan != null) form.setPublicClan(publicClan);
        if (requiresInvitation != null) {
            form.setRequiresInvitation(requiresInvitation);
        }
    }
}
