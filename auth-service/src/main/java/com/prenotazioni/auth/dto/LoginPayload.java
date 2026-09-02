package com.prenotazioni.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** Contenuto di "data" nella risposta di login: token, riepilogo utente, metadati sessione. */
@Getter
@Schema(description = "Contenuto di \"data\" nella risposta di login")
public class LoginPayload {

    @Schema(description = "Token JWT da inviare nell'header Authorization")
    private final String token;
    @Schema(description = "Dati dell'utente autenticato")
    private final UserSummaryDto user;
    @Schema(description = "Momento del login", example = "2026-08-31 14:05:00")
    private final String loginTime;
    @Schema(description = "Schema di autenticazione da usare", example = "Bearer")
    private final String tokenType = "Bearer";

    public LoginPayload(String token, UserSummaryDto user, String loginTime) {
        this.token = token;
        this.user = user;
        this.loginTime = loginTime;
    }
}
