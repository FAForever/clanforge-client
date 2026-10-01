//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.faforever.clan_management.service.ClanAccessRefused;
import com.faforever.clan_management.service.ClanRuleViolation;

// the services refusals as json for every controller in this package, the pages keep their own flash messages
@RestControllerAdvice(basePackages = "com.faforever.clan_management.api")
public class ApiErrorHandler {

    @ExceptionHandler(ClanAccessRefused.class)
    public ResponseEntity<ApiError> refused(ClanAccessRefused e) {
        //System.out.println("403 " + e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(ClanRuleViolation.class)
    public ResponseEntity<ApiError> violated(ClanRuleViolation e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));//400, the request itself broke a clan rule
    }
}
