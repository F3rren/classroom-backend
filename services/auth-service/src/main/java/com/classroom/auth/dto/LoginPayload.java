package com.classroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * The contents of "data" in the login response: token, user summary, session metadata.
 *
 * refreshToken is the newest field here: without it the client would have a token to send in
 * an Authorization header and no way to ever get another one without logging in again. See
 * RefreshTokenService and AuthController's POST /refresh and POST /logout - this is the
 * value both of those endpoints then take as input.
 */
@Getter
@Schema(description = "The contents of \"data\" in the login response")
public class LoginPayload {

    @Schema(description = "The JWT to send in the Authorization header")
    private final String token;
    @Schema(description = "The refresh token: send it to POST /api/auth/refresh for a new token pair, "
            + "or to POST /api/auth/logout to end the session early")
    private final String refreshToken;
    @Schema(description = "The authenticated user's data")
    private final UserSummaryDto user;
    @Schema(description = "The moment of the login", example = "2026-08-31 14:05:00")
    private final String loginTime;
    @Schema(description = "The authentication scheme to use", example = "Bearer")
    private final String tokenType = "Bearer";

    public LoginPayload(String token, String refreshToken, UserSummaryDto user, String loginTime) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.user = user;
        this.loginTime = loginTime;
    }
}
