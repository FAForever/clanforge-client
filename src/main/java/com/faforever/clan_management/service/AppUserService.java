package com.faforever.clan_management.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.ClanMembership;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanInvitationRepository;
import com.faforever.clan_management.repository.ClanMembershipRepository;

@Service
public class AppUserService {

    private final AppUserRepository users;
    private final ClanMembershipRepository memberships;
    private final ClanInvitationRepository invitations;

    public AppUserService(AppUserRepository users, ClanMembershipRepository memberships, ClanInvitationRepository invitations) {
        this.users = users;
        this.memberships = memberships;
        this.invitations = invitations;
    }

    //the principal name is the dev accounts username today and the oidc subject once hydra is wired in, same column either way
    @Transactional
    public Optional<AppUser> find(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        String subject = auth.getName(); //what hydra will send as sub
        //System.out.println("subject " + subject);
        return Optional.of(users.findByOidcSubject(subject).orElseGet(() -> users.save(new AppUser(subject, subject, "local", null))));
    }

    @Transactional
    public AppUser require(Authentication auth) {
        return find(auth).orElseThrow(() -> new IllegalStateException("no signed-in user"));
    }

    @Transactional
    public Optional<SignedInUser> signedIn(Authentication auth) {
        Optional<AppUser> user = find(auth);
        if (user.isEmpty()) return Optional.empty();
        AppUser u = user.get();  // everything the top bar shows, three small queries
        ClanMembership m = memberships.findActiveByUserId(u.getId()).orElse(null);
        long pending = invitations.countOpenForInvitee(u.getId(), OffsetDateTime.now());
        //System.out.println("me " + u.getDisplayName() + " " + pending);
        return Optional.of(new SignedInUser(u, m, pending));
    }

    public Optional<AppUser> findByDisplayName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return users.findByDisplayNameIgnoreCase(name.trim());
    }
}
