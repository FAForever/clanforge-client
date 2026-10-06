package com.faforever.clan_management.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.SignedInUser;

@ControllerAdvice(basePackages = "com.faforever.clan_management.web")
public class CurrentUserAdvice {

    private final AppUserService users;
    private final String adminAuthority;

    public CurrentUserAdvice(AppUserService users, @Value("${clan.admin.role:ADMIN_CLAN}") String adminRole) {
        this.users = users;
        this.adminAuthority = "ROLE_" + adminRole.trim(); // the authority SecurityConfig asks for on /admin
    }

    @ModelAttribute("me")
    public SignedInUser me(Authentication auth) {
        //System.out.println("advice " + auth);
        return users.signedIn(auth).orElse(null);//every template reads ${me} for the top bar, null when nobody is signed in
    }

    // whether the top bar offers the admin pages. Only a link: SecurityConfig and AdminAccess are what keep them closed
    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> adminAuthority.equals(a.getAuthority()));
    }
}
