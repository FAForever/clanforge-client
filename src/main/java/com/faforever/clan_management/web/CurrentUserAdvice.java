package com.faforever.clan_management.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.faforever.clan_management.service.AppUserService;
import com.faforever.clan_management.service.SignedInUser;

@ControllerAdvice(basePackages = "com.faforever.clan_management.web")
public class CurrentUserAdvice {

    private final AppUserService users;

    public CurrentUserAdvice(AppUserService users) {
        this.users = users;
    }

    @ModelAttribute("me")
    public SignedInUser me(Authentication auth) {
        //System.out.println("advice " + auth);
        return users.signedIn(auth).orElse(null);//every template reads ${me} for the top bar, null when nobody is signed in
    }
}
