//SPDX-License-Identifier: MIT
package com.faforever.clan_management.api;

import java.sql.SQLException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.faforever.clan_management.security.ApiBodyLimit;

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
        if (e.getMostSpecificCause() instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("22")) return ResponseEntity.badRequest().body(new ApiError("That request holds a value the database cannot store, such as a nul character.")); //sql state class 22 is bad data sent by the caller, a read can raise it too, so 400 and not a clash
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("That could not be saved. It clashed with another change made at the same time, or held a value the database refuses."));
    }

    // a body that is not the json the route takes, or one sent in chunks past the cap, in the same message shape as every other refusal here
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ApiBodyLimit.TooLarge big) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(new ApiError(big.getMessage()));
        }
        //System.out.println("unreadable " + e.getMessage());
        return ResponseEntity.badRequest().body(new ApiError("The request body could not be read as the JSON this route takes."));
    }
}
