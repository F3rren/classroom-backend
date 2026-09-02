package com.prenotazioni.auth.dto;

import lombok.Getter;

import com.prenotazioni.util.Timestamps;

import java.time.LocalDateTime;

/**
 * Risposta di successo del login. Il token compare sia al livello radice ("token")
 * sia dentro "data" - duplicazione intenzionale del comportamento gia' esistente,
 * mantenuta per compatibilita' con il frontend attuale.
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
