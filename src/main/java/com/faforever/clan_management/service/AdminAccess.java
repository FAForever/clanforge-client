//SPDX-License-Identifier: MIT
package com.faforever.clan_management.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.faforever.clan_management.entity.AppUser;

//who counts as an administrator, asked before anything in AdminService runs
@Component
public class AdminAccess {

    private final String authority;
    private final AppUserService users;

    public AdminAccess(@Value("${clan.admin.role:ADMIN_CLAN}") String role,AppUserService users) {
        String name = role == null ? "" : role.trim(); //trimmed first, so control characters alone count as blank the way TokenRoles counts them
        if (name.isEmpty()) throw new IllegalStateException("clan.admin.role is blank, so nobody could reach /api/admin"); //refused at startup rather than quietly locking everyone out
        if (name.chars().anyMatch(c -> Character.isISOControl(c) || Character.isSpaceChar(c) || Character.isWhitespace(c))) throw new IllegalStateException("clan.admin.role holds a space or a control character, which no role name should"); // trim leaves delete and the c1 controls in place
        this.authority = "ROLE_"+name; // the role name is a property so a deployment outside faforever names its own
        this.users = users;
    }

    public boolean allows(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (authority.equals(a.getAuthority())) return true;
        }
        return false;
    }

    //the app_user row of the administrator who is asking, or the refusal
    public AppUser require(Authentication auth) {
        if (!allows(auth)) throw new ClanAccessRefused("Only an administrator can do that.");
        return users.require(auth);
    }
}
