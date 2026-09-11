package com.classroom.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.classroom.util.Timestamps;

/**
 * A successful login response. The token appears both at the root ("token") and inside
 * "data" - a deliberate duplication of the behaviour that was already there, kept for
 * compatibility with the current frontend.
 *
 * The root token is null - and, per @JsonInclude below, absent rather than "null" - for the
 * same cookie-only callers LoginPayload leaves it out for: see AuthController.login.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean success,
        String message,
        String token,
        LoginPayload data,
        String timestamp,
        String sessionId) {

    public LoginResponse(String message, String token, LoginPayload data, String sessionId) {
        this(true, message, token, data, Timestamps.now(), sessionId);
    }
}
