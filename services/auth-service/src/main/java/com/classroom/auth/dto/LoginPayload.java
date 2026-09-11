package com.classroom.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The contents of "data" in the login response: token, user summary, session metadata.
 *
 * refreshToken is the newest field here: without it the client would have a token to send in
 * an Authorization header and no way to ever get another one without logging in again. See
 * RefreshTokenService and AuthController's POST /refresh and POST /logout - this is the
 * value both of those endpoints then take as input.
 *
 * token and refreshToken are absent (not null - see @JsonInclude below) for a caller that
 * sent SessionCookies.AUTH_MODE_HEADER: it already gets both as HttpOnly cookies, and an
 * HttpOnly cookie protects nothing if the same value sits in clear text right next to it in
 * the response body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The contents of \"data\" in the login response. token and refreshToken are "
        + "absent when the caller asked for the cookie-only response (see X-Auth-Mode): both "
        + "already went out as HttpOnly cookies")
public record LoginPayload(
        @Schema(description = "The JWT to send in the Authorization header - absent in the cookie-only response")
        String token,
        @Schema(description = "The refresh token: send it to POST /api/auth/refresh for a new token pair, "
                + "or to POST /api/auth/logout to end the session early - absent in the cookie-only response")
        String refreshToken,
        @Schema(description = "The authenticated user's data")
        UserSummaryDto user,
        @Schema(description = "The moment of the login", example = "2026-08-31 14:05:00")
        String loginTime,
        @Schema(description = "The authentication scheme to use", example = "Bearer")
        String tokenType) {

    public LoginPayload(String token, String refreshToken, UserSummaryDto user, String loginTime) {
        this(token, refreshToken, user, loginTime, "Bearer");
    }
}
