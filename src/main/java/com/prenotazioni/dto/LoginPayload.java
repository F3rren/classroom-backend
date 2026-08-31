package com.prenotazioni.dto;

/** Contenuto di "data" nella risposta di login: token, riepilogo utente, metadati sessione. */
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

    public String getToken() { return token; }
    public UserSummaryDto getUser() { return user; }
    public String getLoginTime() { return loginTime; }
    public String getTokenType() { return tokenType; }
}
