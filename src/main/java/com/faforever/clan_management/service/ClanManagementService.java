package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.ClanInvitationRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanRepository;

@Service
public class ClanManagementService {

    static final int nameMax = 40;

    private final ClanRepository clans;
    private final ClanMembershipRepository memberships;
    private final ClanInvitationRepository invitations;
    private final MembershipService membershipService;
    private final ClanEventService events;
    private final TagRule tagRule;

    public ClanManagementService(ClanRepository clans, ClanMembershipRepository memberships, ClanInvitationRepository invitations, MembershipService membershipService, ClanEventService events,TagRule tagRule) {
        this.clans = clans;
        this.memberships = memberships;
        this.invitations = invitations;
        this.membershipService = membershipService;
        this.events = events;
        this.tagRule = tagRule;
    }

    //the creator becomes the owner and its first member in the same transaction, the way faf-java-api does it
    @Transactional
    public Clan create(AppUser owner, ClanForm form) {
        if (memberships.findActiveByUserId(owner.getId()).isPresent()) throw new ClanRuleViolation("You are already in a clan. Leave it before creating another.");
        check(form, null);
        Clan clan = clans.save(new Clan(form.getName().trim(), form.getTag().trim(),blankToNull(form.getDescription()),owner, form.isPublicClan() ? "PUBLIC" : "PRIVATE", form.isRequiresInvitation()));
        ClanMembership m = memberships.save(new ClanMembership(clan, owner, MembershipService.ownerRole)); // the owner is a member too, faf requires the leader in the clan
        events.record("clan", clan.getId(), "ClanCreated", events.clanPayload(clan), owner);
        events.record("clan_membership",m.getId(), "MemberJoined", events.membershipPayload(m),owner);
        //System.out.println("created " + clan.getTag());
        return clan;
    }

    @Transactional
    public Clan update(AppUser actor, ClanForm form) {
        ClanMembership me = membershipService.manager(actor, "Only the owner and moderators can edit the clan.");
        Clan clan = me.getClan();
        check(form, clan);
        clan.setName(form.getName().trim());
        clan.setTag(form.getTag().trim());
        clan.setDescription(blankToNull(form.getDescription()));
        clan.setVisibility(form.isPublicClan() ? "PUBLIC" : "PRIVATE");
        clan.setRequiresInvitation(form.isRequiresInvitation());
        clan.touch();
        events.record("clan", clan.getId(), "ClanUpdated", events.clanPayload(clan), actor);
        return clan;
    }

    @Transactional
    public void delete(AppUser actor) {
        ClanMembership me = owner(actor, "Only the owner can delete the clan.");
        Clan clan = me.getClan();
        OffsetDateTime now = OffsetDateTime.now(); // a soft delete: the row stays for the history table, memberships close and open invites are revoked
        int gone = 0;
        for (ClanMembership m : memberships.findRoster(clan.getId())) {
            m.setLeftAt(now);
            gone++;
        }
        for (ClanInvitation i : invitations.findByClanIdAndState(clan.getId(), "PENDING")) {
            i.setState("REVOKED");
            i.setRespondedAt(now);
        }
        clan.setDeletedAt(now);
        clan.touch();
        Map<String, Object> payload = events.clanPayload(clan);
        payload.put("membersRemoved", gone);
        events.record("clan", clan.getId(), "ClanDeleted", payload, actor);
        //System.out.println("deleted " + clan.getTag() + " " + gone);
    }

    // the old owner stays on as a moderator, matching what the settings page promised
    @Transactional
    public void transferOwnership(AppUser actor,Long newOwnerId) {
        ClanMembership me = owner(actor, "Only the owner can transfer ownership.");
        Clan clan = me.getClan();
        ClanMembership target = memberships.findActiveByClanIdAndUserId(clan.getId(),newOwnerId).orElseThrow(() -> new ClanRuleViolation("Pick a current member of the clan."));
        if (target.getId().equals(me.getId())) throw new ClanRuleViolation("You already own the clan.");
        Long previous = clan.getOwner().getId();
        clan.setOwner(target.getUser());
        clan.touch();
        target.setRole(MembershipService.ownerRole);
        me.setRole(MembershipService.moderatorRole);
        Map<String, Object> payload = events.clanPayload(clan);
        payload.put("previousOwnerId", previous);
        events.record("clan",clan.getId(), "OwnershipTransferred", payload, actor);
    }

    private ClanMembership owner(AppUser actor,String refusal) {
        ClanMembership me = memberships.findActiveByUserId(actor.getId()).orElseThrow(() -> new ClanRuleViolation("You are not in a clan."));
        if (!MembershipService.ownerRole.equals(me.getRole())) throw new ClanRuleViolation(refusal);
        return me;
    }

    private void check(ClanForm form, Clan self) {
        String name = form.getName() == null ? "" : form.getName().trim();
        String tag = form.getTag() == null ? "" : form.getTag().trim();//name and tag rules, then the two uniqueness checks the database also enforces, so the person sees a sentence instead of a stack trace
        if (name.isEmpty()) throw new ClanRuleViolation("Give the clan a name.");
        if (name.length() > nameMax) throw new ClanRuleViolation("Clan names are " + nameMax+" characters at most.");
        if (!tagRule.accepts(tag)) throw new ClanRuleViolation("Clan tag must be "+tagRule.getHint() + ".");
        Optional<Clan> sameName = clans.findByNameIgnoreCaseAndDeletedAtIsNull(name);
        if (sameName.isPresent() && (self == null || !sameName.get().getId().equals(self.getId()))) throw new ClanRuleViolation("The clan name '" + name + "' is already in use.");
        Optional<Clan> sameTag = clans.findByTagIgnoreCaseAndDeletedAtIsNull(tag);
        if (sameTag.isPresent() && (self == null || !sameTag.get().getId().equals(self.getId()))) throw new ClanRuleViolation("The clan tag '"+tag + "' is already in use.");
        //System.out.println("checked " + name + " " + tag);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
