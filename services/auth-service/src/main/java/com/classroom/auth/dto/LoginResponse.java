package com.classroom.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

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
@Getter
public class LoginResponse {


    private final boolean success = true;
    private final String message;
    private final String token;
    private final LoginPayload data;
    private final String timestamp;
    private final String sessionId;

    public LoginResponse(String message, String token, LoginPayload data, String sessionId) {
        this.message = message;
        this.token = token;
        this.data = data;
        this.timestamp = Timestamps.now();
        this.sessionId = sessionId;
    }
}
