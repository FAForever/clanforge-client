//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import org.springframework.dao.DataIntegrityViolationException;
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

    //a database constraint turned the write down, mostly two requests racing for the same unique row, so 409 and no stack trace
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> clashed(DataIntegrityViolationException e) {
        //System.out.println("409 " + e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("That could not be saved. It clashed with another change made at the same time, or held a value the database refuses."));
    }
}
