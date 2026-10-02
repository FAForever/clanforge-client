package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanRepository;

@Service
public class MembershipService {

    static final String ownerRole = "OWNER";
    static final String moderatorRole = "MODERATOR";
    static final String memberRole = "MEMBER";

    private final ClanRepository clans;
    private final ClanMembershipRepository memberships;
    private final ClanEventService events;

    public MembershipService(ClanRepository clans, ClanMembershipRepository memberships, ClanEventService events) {
        this.clans = clans;
        this.memberships = memberships;
        this.events = events;
    }

    public Optional<ClanMembership> activeMembership(AppUser user) {
        return memberships.findActiveByUserId(user.getId());
    }

    public List<MemberCard> roster(Clan clan) {
        List<MemberCard> cards = new ArrayList<>();
        for (ClanMembership m : memberships.findRoster(clan.getId())) {
            cards.add(new MemberCard(m)); //one card per active row, owner first
        }
        //System.out.println("roster " + clan.getTag() + " " + cards.size());
        return cards;
    }

    @Transactional
    public ClanMembership join(Long clanId, AppUser user) {
        Clan clan = clans.findByIdAndDeletedAtIsNull(clanId).orElseThrow(() -> new ClanRuleViolation("That clan does not exist."));//a public clan that does not ask for an invitation can be joined straight from its page
        if (!"PUBLIC".equals(clan.getVisibility()) || clan.isRequiresInvitation()) throw new ClanRuleViolation("That clan only takes new members by invitation.");
        if (!"ACTIVE".equals(clan.getStatus())) throw new ClanRuleViolation("That clan is suspended.");
        if (memberships.findActiveByUserId(user.getId()).isPresent()) throw new ClanRuleViolation("Leave your current clan before joining another.");
        ClanMembership m = memberships.save(new ClanMembership(clan, user, memberRole));
        events.record("clan_membership", m.getId(), "MemberJoined", events.membershipPayload(m), user);
        //System.out.println("joined " + clan.getTag() + " " + user.getDisplayName());
        return m;
    }

    @Transactional
    public void leave(AppUser user) {
        ClanMembership m = memberships.findActiveByUserId(user.getId()).orElseThrow(() -> new ClanRuleViolation("You are not in a clan."));
        leave(m, user);
    }

    @Transactional
    public void leave(Long clanId, AppUser user) {
        ClanMembership m = memberships.findActiveByUserId(user.getId()).orElseThrow(() -> new ClanRuleViolation("You are not in a clan."));
        if (!m.getClan().getId().equals(clanId)) {
            throw new ClanAccessRefused("You can only leave your own clan.");
        }
        leave(m, user);
    }

    private void leave(ClanMembership m, AppUser user) {
        if (ownerRole.equals(m.getRole())) throw new ClanRuleViolation("Transfer ownership or delete the clan before leaving it."); // the owner leaves by handing the clan over or deleting it, never by walking out
        m.setLeftAt(OffsetDateTime.now());
        events.record("clan_membership", m.getId(), "MemberLeft", events.membershipPayload(m), user);
    }

    // the owner can remove anyone but themselves, a moderator only ordinary members
    @Transactional
    public void removeMember(AppUser actor, Long userId) {
        ClanMembership me = manager(actor, "Only the owner and moderators can remove members.");
        ClanMembership target = memberships.findActiveByClanIdAndUserId(me.getClan().getId(), userId).orElseThrow(() -> new ClanRuleViolation("That player is not in your clan."));
        if (target.getId().equals(me.getId())) throw new ClanRuleViolation("Use leave to remove yourself.");
        if (ownerRole.equals(target.getRole())) throw new ClanRuleViolation("The owner cannot be removed.");
        if (moderatorRole.equals(me.getRole()) && moderatorRole.equals(target.getRole())) throw new ClanAccessRefused("A moderator cannot remove another moderator.");
        target.setLeftAt(OffsetDateTime.now());
        //System.out.println("removed " + target.getUser().getDisplayName());
        events.record("clan_membership",target.getId(), "MemberRemoved", events.membershipPayload(target), actor);
    }

    @Transactional
    public void setRole(AppUser actor, Long userId, String role) {
        ClanMembership me = memberships.findActiveByUserId(actor.getId()).orElseThrow(() -> new ClanAccessRefused("You are not in a clan."));
        if (!ownerRole.equals(me.getRole())) throw new ClanAccessRefused("Only the owner can change roles.");//only the owner hands out or takes back the moderator role
        if (!moderatorRole.equals(role) && !memberRole.equals(role)) throw new ClanRuleViolation("That role does not exist.");
        ClanMembership target = memberships.findActiveByClanIdAndUserId(me.getClan().getId(), userId).orElseThrow(() -> new ClanRuleViolation("That player is not in your clan."));
        if (target.getId().equals(me.getId())) throw new ClanRuleViolation("The owner keeps the owner role until it is transferred.");
        target.setRole(role);
        events.record("clan_membership", target.getId(), "MemberRoleChanged", events.membershipPayload(target), actor);
    }

    // the membership of someone allowed to run the clan, owner or moderator, or the refusal to show
    public ClanMembership manager(AppUser actor, String refusal) {
        ClanMembership me = memberships.findActiveByUserId(actor.getId()).orElseThrow(() -> new ClanAccessRefused("You are not in a clan."));
        if (!ownerRole.equals(me.getRole()) && !moderatorRole.equals(me.getRole())) throw new ClanAccessRefused(refusal);
        //System.out.println("manager " + me.getRole());
        return me;
    }
}
