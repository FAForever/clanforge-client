package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanInvitationRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;

@Service
public class InvitationService {

    private final ClanInvitationRepository invitations;
    private final ClanMembershipRepository memberships;
    private final AppUserRepository users;
    private final MembershipService membershipService;
    private final ClanEventService events;
    private final int expiryDays;

    public InvitationService(ClanInvitationRepository invitations, ClanMembershipRepository memberships, AppUserRepository users, MembershipService membershipService, ClanEventService events, @Value("${clan.invitation.expiry-days:7}") int expiryDays) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.users = users;
        this.membershipService = membershipService;
        this.events = events;
        this.expiryDays = expiryDays;
    }

    public List<InvitationCard> inbox(AppUser user) {
        return cards(invitations.findOpenForInvitee(user.getId(), OffsetDateTime.now()));
    }

    public List<InvitationCard> sent(Clan clan) {
        return cards(invitations.findOpenForClan(clan.getId(), OffsetDateTime.now()));
    }

    @Transactional
    public ClanInvitation invite(AppUser actor, String inviteeName, String role) {
        return send(membershipService.manager(actor, "Only the owner and moderators can send invites."), actor, inviteeName, role);
    }

    //the api names the clan in its path, so the clan the caller runs has to be that one before anything is sent
    @Transactional
    public ClanInvitation invite(AppUser actor, Long clanId,String inviteeName, String role) {
        ClanMembership me = membershipService.manager(actor, "Only the owner and moderators can send invites.");
        //System.out.println("invite path " + clanId + " mine " + me.getClan().getId());
        if (!me.getClan().getId().equals(clanId)) throw new ClanAccessRefused("You can only invite players to your own clan.");
        return send(me, actor, inviteeName, role);
    }

    private ClanInvitation send(ClanMembership me, AppUser actor, String inviteeName, String role) {
        Clan clan = me.getClan();
        String offered = role == null || role.isBlank() ? MembershipService.memberRole : role.trim();
        if (!MembershipService.memberRole.equals(offered) && !MembershipService.moderatorRole.equals(offered)) throw new ClanRuleViolation("That role does not exist.");
        if (MembershipService.moderatorRole.equals(offered) && !MembershipService.ownerRole.equals(me.getRole())) throw new ClanAccessRefused("Only the owner can invite as moderator.");//a moderator hands out the member role only
        String name = inviteeName == null ? "" : inviteeName.trim(); //owner or moderator invites by display name, the imported players are User_<id> until real names arrive
        AppUser invitee = users.findByDisplayNameIgnoreCase(name).orElseThrow(() -> new ClanRuleViolation("No player named '" + name + "'."));
        if (invitee.getId().equals(actor.getId())) throw new ClanRuleViolation("You are already in the clan.");
        if (memberships.findActiveByClanIdAndUserId(clan.getId(), invitee.getId()).isPresent()) throw new ClanRuleViolation(invitee.getDisplayName() + " is already a member.");
        Optional<ClanInvitation> open = invitations.findByClanIdAndInviteeIdAndState(clan.getId(), invitee.getId(), "PENDING");
        if (open.isPresent() && open.get().getExpiresAt().isAfter(OffsetDateTime.now())) throw new ClanRuleViolation(invitee.getDisplayName() + " already has a pending invite.");
        if (open.isPresent()) {
            open.get().setState("EXPIRED"); //a lapsed invite is only marked when someone acts on it, and it must be marked before the new row goes in or the one-pending index refuses it
            open.get().setRespondedAt(OffsetDateTime.now());
            invitations.saveAndFlush(open.get());
        }
        ClanInvitation inv = invitations.save(new ClanInvitation(clan, invitee, actor, OffsetDateTime.now().plusDays(expiryDays), offered)); //expires_at is set here, the effective view derives expired from it
        events.record("clan_invitation", inv.getId(), "InvitationCreated", events.invitationPayload(inv), actor);
        //System.out.println("invited " + invitee.getDisplayName() + " to " + clan.getTag());
        return inv;
    }

    @Transactional
    public ClanMembership accept(AppUser user, Long id) {
        ClanInvitation inv = open(user, id);
        Clan clan = inv.getClan();  // accepting joins the clan in the same transaction, one active membership per person is the database rule this leans on
        if (clan.getDeletedAt() != null || !"ACTIVE".equals(clan.getStatus())) throw new ClanRuleViolation("That clan is no longer taking members.");
        if (memberships.findActiveByUserId(user.getId()).isPresent()) throw new ClanRuleViolation("Leave your current clan before accepting an invite.");
        inv.setState("ACCEPTED");
        inv.setRespondedAt(OffsetDateTime.now());
        ClanMembership m = memberships.save(new ClanMembership(clan, user, inv.getRole())); //the role the invite offered, member unless the owner said moderator
        events.record("clan_invitation", inv.getId(), "InvitationAccepted", events.invitationPayload(inv),user);
        events.record("clan_membership", m.getId(), "MemberJoined",events.membershipPayload(m), user);
        //System.out.println("accepted " + id);
        return m;
    }

    @Transactional
    public void decline(AppUser user, Long id) {
        ClanInvitation inv = open(user,id);
        inv.setState("DECLINED");
        inv.setRespondedAt(OffsetDateTime.now());
        events.record("clan_invitation", inv.getId(), "InvitationDeclined", events.invitationPayload(inv), user);
    }

    @Transactional
    public void revoke(AppUser actor, Long id) {
        ClanMembership me = membershipService.manager(actor,"Only the owner and moderators can revoke invites.");
        ClanInvitation inv = invitations.findById(id).orElseThrow(() -> new ClanRuleViolation("That invitation does not exist."));
        if (!inv.getClan().getId().equals(me.getClan().getId())) throw new ClanRuleViolation("That invitation does not exist."); //another clans invitation reads the same as a missing one, so ids cannot be probed
        if (!"PENDING".equals(inv.getState())) throw new ClanRuleViolation("That invitation is no longer open.");
        inv.setState("REVOKED");
        inv.setRespondedAt(OffsetDateTime.now());
        //System.out.println("revoked " + id);
        events.record("clan_invitation", inv.getId(), "InvitationRevoked", events.invitationPayload(inv), actor);
    }

    private ClanInvitation open(AppUser user,Long id) {
        ClanInvitation inv = invitations.findById(id).orElseThrow(() -> new ClanRuleViolation("That invitation does not exist.")); // an invitation the given person may still answer: theirs, pending, and not past its expiry
        if (!inv.getInvitee().getId().equals(user.getId())) throw new ClanRuleViolation("That invitation does not exist."); // someone elses invitation reads the same as a missing one
        if (!"PENDING".equals(inv.getState())) throw new ClanRuleViolation("That invitation is no longer open.");
        if (!inv.getExpiresAt().isAfter(OffsetDateTime.now())) throw new ClanRuleViolation("That invitation has expired.");
        return inv;
    }

    private List<InvitationCard> cards(List<ClanInvitation> rows) {
        List<InvitationCard> out = new ArrayList<>();
        for (ClanInvitation i : rows) {
            out.add(new InvitationCard(i));
        }
        //System.out.println("cards " + out.size());
        return out;
    }
}
