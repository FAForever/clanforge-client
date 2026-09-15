package com.faforever.clan_management.service;

import com.faforever.clan_management.entity.Clan;

//what the create and settings forms post, checked by ClanManagementService before anything is saved
public class ClanForm {

    private String name = "";
    private String tag = "";
    private String description = "";
    private boolean publicClan = true;
    private boolean requiresInvitation = false;

    public static ClanForm of(Clan clan) {
        ClanForm f = new ClanForm();
        f.setName(clan.getName());
        f.setTag(clan.getTag());
        f.setDescription(clan.getDescription() == null ? "" : clan.getDescription());
        f.setPublicClan("PUBLIC".equals(clan.getVisibility()));
        f.setRequiresInvitation(clan.isRequiresInvitation());
        //System.out.println("form " + f.getTag());
        return f;
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

    public boolean isPublicClan() {
        return publicClan;
    }

    public void setPublicClan(boolean publicClan) {
        this.publicClan = publicClan;
    }

    public boolean isRequiresInvitation() {
        return requiresInvitation;
    }

    public void setRequiresInvitation(boolean requiresInvitation) {
        this.requiresInvitation = requiresInvitation;
    }
}
