package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.entity.ClanInvitation;
import com.faforever.clan_management.entity.ClanLogo;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.ClanInvitationRepository;
import com.faforever.clan_management.repository.ClanLogoRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;
import com.faforever.clan_management.repository.ClanRepository;

@Service
public class ClanManagementService {

    static final int nameMax = 40;
    static final int motdMax = 500;
    static final int logoMax = 524288;

    private final ClanRepository clans;
    private final ClanMembershipRepository memberships;
    private final ClanInvitationRepository invitations;
    private final ClanLogoRepository logos;
    private final MembershipService membershipService;
    private final ClanEventService events;
    private final TagRule tagRule;
    private final Regions regions;

    public ClanManagementService(ClanRepository clans, ClanMembershipRepository memberships,ClanInvitationRepository invitations, ClanLogoRepository logos, MembershipService membershipService, ClanEventService events, TagRule tagRule, Regions regions) {
        this.clans = clans;
        this.memberships = memberships;
        this.invitations = invitations;
        this.logos = logos;
        this.membershipService = membershipService;
        this.events = events;
        this.tagRule = tagRule;
        this.regions = regions;
    }

    //the creator becomes the owner and its first member in the same transaction, the way faf-java-api does it
    @Transactional
    public Clan create(AppUser owner, ClanForm form) {
        if (memberships.findActiveByUserId(owner.getId()).isPresent()) throw new ClanRuleViolation("You are already in a clan. Leave it before creating another.");
        check(form,null);
        Clan clan = new Clan(form.getName().trim(), form.getTag().trim(), blankToNull(form.getDescription()),owner, form.isPublicClan() ? "PUBLIC" : "PRIVATE", form.isRequiresInvitation());
        clan.setRegion(blankToNull(form.getRegion()));
        clan = clans.save(clan);
        ClanMembership m = memberships.save(new ClanMembership(clan, owner, MembershipService.ownerRole)); // the owner is a member too, faf requires the leader in the clan
        events.record("clan", clan.getId(), "ClanCreated", events.clanPayload(clan), owner);
        events.record("clan_membership", m.getId(), "MemberJoined", events.membershipPayload(m), owner);
        //System.out.println("created " + clan.getTag());
        return clan;
    }

    @Transactional
    public Clan update(AppUser actor, ClanForm form) {
        ClanMembership me = membershipService.manager(actor,"Only the owner and moderators can edit the clan.");
        return update(me, actor, form);
    }

    @Transactional
    public Clan update(AppUser actor, Long clanId, ClanForm form) {
        ClanMembership me = membershipService.manager(actor,"Only the owner and moderators can edit the clan.");
        if (!me.getClan().getId().equals(clanId)) {
            throw new ClanAccessRefused("You can only edit your own clan.");
        }
        return update(me, actor, form);
    }

    @Transactional
    public Clan patch(AppUser actor, Long clanId, ClanPatch patch) {
        ClanMembership me = membershipService.manager(actor,"Only the owner and moderators can edit the clan.");
        if (!me.getClan().getId().equals(clanId)) {
            throw new ClanAccessRefused("You can only edit your own clan.");
        }
        ClanForm form = ClanForm.of(me.getClan());
        patch.applyTo(form);
        return update(me, actor, form);
    }

    private Clan update(ClanMembership me, AppUser actor, ClanForm form) {
        Clan clan = me.getClan();
        check(form, clan);
        clan.setName(form.getName().trim());
        clan.setTag(form.getTag().trim());
        clan.setDescription(blankToNull(form.getDescription()));
        clan.setRegion(blankToNull(form.getRegion()));
        clan.setVisibility(form.isPublicClan() ? "PUBLIC" : "PRIVATE");
        clan.setRequiresInvitation(form.isRequiresInvitation());
        clan.touch();
        events.record("clan", clan.getId(), "ClanUpdated",events.clanPayload(clan), actor);
        return clan;
    }

    //the message of the day, shown on the dashboard to every member with who posted it
    @Transactional
    public Clan updateMotd(AppUser actor, String text) {
        ClanMembership me = membershipService.manager(actor, "Only the owner and moderators can change the message of the day.");
        Clan clan = me.getClan();
        String motd = blankToNull(text);
        if (motd != null && motd.length() > motdMax) throw new ClanRuleViolation("The message of the day is " + motdMax+" characters at most.");
        clan.setMotd(motd, actor);
        clan.touch();
        events.record("clan",clan.getId(), "ClanMotdUpdated", events.clanPayload(clan), actor);
        //System.out.println("motd " + clan.getTag());
        return clan;
    }

    // what kind of image this really is, from its first bytes rather than the name the browser sent, or the refusal
    public String checkImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new ClanRuleViolation("Choose an image file.");
        if (bytes.length > logoMax) throw new ClanRuleViolation("Images are 512 KB at most.");
        if (bytes.length > 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length > 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        //System.out.println("image refused " + bytes.length);
        throw new ClanRuleViolation("Use a PNG, JPG or WEBP image.");
    }

    @Transactional
    public Clan setLogo(AppUser actor,byte[] bytes) {
        ClanMembership me = membershipService.manager(actor, "Only the owner and moderators can change the clan image.");
        Clan clan = me.getClan();
        String type = checkImage(bytes);
        Optional<ClanLogo> current = logos.findById(clan.getId());
        if (current.isPresent()) {
            current.get().replace(bytes,type);
        } else {
            logos.save(new ClanLogo(clan.getId(), bytes,type));
        }
        clan.setLogoUpdatedAt(OffsetDateTime.now());
        clan.touch();
        events.record("clan", clan.getId(),"ClanLogoUpdated", events.clanPayload(clan), actor);
        return clan;
    }

    @Transactional
    public Clan removeLogo(AppUser actor) {
        ClanMembership me = membershipService.manager(actor, "Only the owner and moderators can change the clan image.");
        Clan clan = me.getClan();
        logos.deleteById(clan.getId());
        clan.setLogoUpdatedAt(null);
        clan.touch();
        events.record("clan", clan.getId(),"ClanLogoRemoved",events.clanPayload(clan), actor);
        return clan;
    }

    //the image of a clan anyone may see, or of the viewers own clan when it is private
    public Optional<ClanLogo> logo(Long clanId, Optional<AppUser> viewer) {
        Optional<Clan> clan = clans.findByIdAndDeletedAtIsNull(clanId);
        if (clan.isEmpty()) return Optional.empty();
        boolean member = viewer.isPresent() && memberships.findActiveByClanIdAndUserId(clanId,viewer.get().getId()).isPresent();
        if (!"PUBLIC".equals(clan.get().getVisibility()) && !member) return Optional.empty();
        return logos.findById(clanId);
    }

    @Transactional
    public void delete(AppUser actor) {
        ClanMembership me = owner(actor, "Only the owner can delete the clan.");
        delete(me, actor);
    }

    @Transactional
    public void delete(AppUser actor, Long clanId) {
        ClanMembership me = owner(actor, "Only the owner can delete the clan.");
        if (!me.getClan().getId().equals(clanId)) {
            throw new ClanAccessRefused("You can only delete your own clan.");
        }
        delete(me, actor);
    }

    private void delete(ClanMembership me, AppUser actor) {
        Clan clan = me.getClan();
        int gone = close(clan);
        Map<String,Object> payload = events.clanPayload(clan);
        payload.put("membersRemoved",gone);
        events.record("clan", clan.getId(),"ClanDeleted", payload, actor);
        //System.out.println("deleted " + clan.getTag() + " " + gone);
    }

    //what deleting does to the rows, shared by the owner deleting their clan and an administrator deleting any clan
    int close(Clan clan) {
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
        return gone;
    }

    // the old owner stays on as a moderator, matching what the settings page promised
    @Transactional
    public void transferOwnership(AppUser actor, Long newOwnerId) {
        ClanMembership me = owner(actor, "Only the owner can transfer ownership.");
        Clan clan = me.getClan();
        ClanMembership target = memberships.findActiveByClanIdAndUserId(clan.getId(), newOwnerId).orElseThrow(() -> new ClanRuleViolation("Pick a current member of the clan."));
        if (target.getId().equals(me.getId())) throw new ClanRuleViolation("You already own the clan.");
        Long previous = clan.getOwner().getId();
        clan.setOwner(target.getUser());
        clan.touch();
        target.setRole(MembershipService.ownerRole);
        me.setRole(MembershipService.moderatorRole);
        Map<String, Object> payload = events.clanPayload(clan);
        payload.put("previousOwnerId", previous);
        events.record("clan", clan.getId(), "OwnershipTransferred", payload, actor);
    }

    private ClanMembership owner(AppUser actor, String refusal) {
        ClanMembership me = membershipService.lockedMembership(actor).orElseThrow(() -> new ClanAccessRefused("You are not in a clan.")); //the clan locked before the role is read, see lockedMembership
        if (!MembershipService.ownerRole.equals(me.getRole())) throw new ClanAccessRefused(refusal);
        return me;
    }

    private void check(ClanForm form, Clan self) {
        String name = form.getName() == null ? "" : form.getName().trim();
        String tag = form.getTag() == null ? "" : form.getTag().trim();//name and tag rules, then the two uniqueness checks the database also enforces, so the person sees a sentence instead of a stack trace
        nameShape(name);
        tagShape(tag);
        if (!regions.accepts(form.getRegion())) throw new ClanRuleViolation("Pick a region from the list.");
        nameFree(name, self);
        tagFree(tag, self);
        //System.out.println("checked " + name + " " + tag);
    }

    //the name rules on their own, for an administrator renaming a clan that is not theirs
    void checkName(String name, Clan self) {
        nameShape(name);
        nameFree(name, self);
    }

    void checkTag(String tag, Clan self) {
        tagShape(tag);
        tagFree(tag,self);
    }

    private void nameShape(String name) {
        if (name.isEmpty()) throw new ClanRuleViolation("Give the clan a name.");
        if (name.length() > nameMax) throw new ClanRuleViolation("Clan names are " + nameMax + " characters at most.");
    }

    private void tagShape(String tag) {
        if (!tagRule.accepts(tag)) throw new ClanRuleViolation("Clan tag must be " + tagRule.getHint() + ".");
    }

    private void nameFree(String name,Clan self) {
        Optional<Clan> sameName = clans.findByNameIgnoreCaseAndDeletedAtIsNull(name);
        if (sameName.isPresent() && (self == null || !sameName.get().getId().equals(self.getId()))) throw new ClanRuleViolation("The clan name '" + name + "' is already in use.");
    }

    private void tagFree(String tag, Clan self) {
        Optional<Clan> sameTag = clans.findByTagIgnoreCaseAndDeletedAtIsNull(tag);
        if (sameTag.isPresent() && (self == null || !sameTag.get().getId().equals(self.getId()))) throw new ClanRuleViolation("The clan tag '" + tag + "' is already in use.");
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
