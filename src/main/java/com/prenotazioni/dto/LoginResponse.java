package com.prenotazioni.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Risposta di successo del login. Il token compare sia al livello radice ("token")
 * sia dentro "data" - duplicazione intenzionale del comportamento gia' esistente,
 * mantenuta per compatibilita' con il frontend attuale.
 */
public class LoginResponse {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        this.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        this.sessionId = sessionId;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getToken() { return token; }
    public LoginPayload getData() { return data; }
    public String getTimestamp() { return timestamp; }
    public String getSessionId() { return sessionId; }
}
