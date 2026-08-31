package com.prenotazioni.dto;

import lombok.Getter;

/** Contenuto di "data" nella risposta di login: token, riepilogo utente, metadati sessione. */
@Getter
public class LoginPayload {

    private final String token;
    private final UserSummaryDto user;
    private final String loginTime;
    private final String tokenType = "Bearer";

    public LoginPayload(String token, UserSummaryDto user, String loginTime) {
        this.token = token;
        this.user = user;
        this.loginTime = loginTime;
    }
}
