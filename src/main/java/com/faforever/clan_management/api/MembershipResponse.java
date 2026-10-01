//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.time.OffsetDateTime;

import com.faforever.clan_management.entity.ClanMembership;

//the clan someone just joined and the role they hold in it
public record MembershipResponse(Long clanId, String clanName, String clanTag, Long userId,String role,OffsetDateTime joinedAt) {

    public static MembershipResponse from(ClanMembership m) {
        return new MembershipResponse(m.getClan().getId(), m.getClan().getName(), m.getClan().getTag(), m.getUser().getId(), m.getRole(), m.getJoinedAt());
    }
}
