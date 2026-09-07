package com.prenotazioni.auth.dto;

import lombok.Getter;

import com.prenotazioni.util.Timestamps;

/**
 * A successful login response. The token appears both at the root ("token") and inside
 * "data" - a deliberate duplication of the behaviour that was already there, kept for
 * compatibility with the current frontend.
 */
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
