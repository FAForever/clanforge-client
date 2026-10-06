package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
        if (!clans.existsByIdAndVisibilityAndStatusAndRequiresInvitationFalseAndDeletedAtIsNull(clanId,"PUBLIC", "ACTIVE")) { //asked before the lock, so a clan that would refuse anyway never makes the request wait
            if (!clans.existsByIdAndDeletedAtIsNull(clanId)) throw new ClanRuleViolation("That clan does not exist.");
            if (!clans.existsByIdAndVisibilityAndRequiresInvitationFalseAndDeletedAtIsNull(clanId, "PUBLIC")) throw new ClanRuleViolation("That clan only takes new members by invitation.");
            throw new ClanRuleViolation("That clan is suspended.");
        }
        if (memberships.existsByUserIdAndLeftAtIsNull(user.getId())) throw new ClanRuleViolation("Leave your current clan before joining another."); // the same, for someone already in a clan
        Clan clan = clans.lockLive(clanId).orElseThrow(() -> new ClanRuleViolation("That clan does not exist."));//a public clan that does not ask for an invitation can be joined straight from its page
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
    ClanMembership membership = lockedMembership(user)
            .orElseThrow(() ->
                    new ClanRuleViolation("You are not in a clan."));
    leave(membership, user);
}

@Transactional
public void leave(Long clanId, AppUser user) {
    ClanMembership membership = lockedMembership(user)
            .orElseThrow(() ->
                    new ClanRuleViolation("You are not in a clan."));

    if (!membership.getClan().getId().equals(clanId)) {
        throw new ClanAccessRefused(
                "You can only leave your own clan."
        );
    }

    leave(membership, user);
}

private void leave(ClanMembership membership, AppUser user) {
    if (ownerRole.equals(membership.getRole())) {
        throw new ClanRuleViolation(
                "Transfer ownership or delete the clan before leaving it."
        );
    }

    membership.setLeftAt(OffsetDateTime.now());
    events.record(
            "clan_membership",
            membership.getId(),
            "MemberLeft",
            events.membershipPayload(membership),
            user
    );
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
        ClanMembership me = lockedMembership(actor).orElseThrow(() -> new ClanAccessRefused("You are not in a clan."));
        if (!ownerRole.equals(me.getRole())) throw new ClanAccessRefused("Only the owner can change roles.");//only the owner hands out or takes back the moderator role
        requireActive(me.getClan()); // setRole has its own gate, not manager(), so the freeze is applied here too
        if (!moderatorRole.equals(role) && !memberRole.equals(role)) throw new ClanRuleViolation("That role does not exist.");
        ClanMembership target = memberships.findActiveByClanIdAndUserId(me.getClan().getId(), userId).orElseThrow(() -> new ClanRuleViolation("That player is not in your clan."));
        if (target.getId().equals(me.getId())) throw new ClanRuleViolation("The owner keeps the owner role until it is transferred.");
        target.setRole(role);
        events.record("clan_membership", target.getId(), "MemberRoleChanged", events.membershipPayload(target), actor);
    }

    // A suspended clan is frozen: its owner and moderators cannot change it (edit settings,
    // invite, change roles, transfer, delete) until an administrator lifts the suspension.
    // Joining and accepting an invite are refused separately, in join() and InvitationService;
    // a member may still leave. Called from the owner/mod gates (manager, setRole, and the
    // owner() gate in ClanManagementService), never from the admin path, so moderation works.
    public void requireActive(Clan clan) {
        if ("SUSPENDED".equals(clan.getStatus())) {
            throw new ClanRuleViolation("This clan is suspended. An administrator must lift the suspension before it can be changed.");
        }
    }

    // the membership of someone allowed to run the clan, owner or moderator, or the refusal to show
    @Transactional(propagation = Propagation.MANDATORY) //it takes the clan lock, which only means something inside the caller's transaction
    public ClanMembership manager(AppUser actor, String refusal) {
        ClanMembership me = lockedMembership(actor).orElseThrow(() -> new ClanAccessRefused("You are not in a clan."));
        if (!ownerRole.equals(me.getRole()) && !moderatorRole.equals(me.getRole())) throw new ClanAccessRefused(refusal);
        requireActive(me.getClan()); // the one gate every owner/mod change passes through, so a suspended clan is frozen here
        //System.out.println("manager " + me.getRole());
        return me;
    }

    //the clan row locked until the transaction ends, empty once it is deleted, so an owner, a moderator and an administrator acting on one clan take turns
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Clan> lockClan(Long clanId) {
        return clans.lockLive(clanId);
    }

    // the clan locked first and the membership read after it, so the role checked is the one whoever held the lock left behind, and a clan deleted meanwhile reads as no clan
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ClanMembership> lockedMembership(AppUser user) {
        Long clanId = memberships.findActiveClanIdByUserId(user.getId()).orElse(null);
        if (clanId == null || lockClan(clanId).isEmpty()) return Optional.empty();
        //System.out.println("locked clan " + clanId + " for " + user.getId());
        return memberships.findActiveByUserId(user.getId()).filter(m -> m.getClan().getId().equals(clanId)); //empty if they were removed, or moved to another clan, while this waited
    }
}
