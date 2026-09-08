package com.classroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** The contents of "data" in the login response: token, user summary, session metadata. */
@Getter
@Schema(description = "The contents of \"data\" in the login response")
public class LoginPayload {

    @Schema(description = "The JWT to send in the Authorization header")
    private final String token;
    @Schema(description = "The authenticated user's data")
    private final UserSummaryDto user;
    @Schema(description = "The moment of the login", example = "2026-08-31 14:05:00")
    private final String loginTime;
    @Schema(description = "The authentication scheme to use", example = "Bearer")
    private final String tokenType = "Bearer";

    public LoginPayload(String token, UserSummaryDto user, String loginTime) {
        this.token = token;
        this.user = user;
        this.loginTime = loginTime;
    }
}
