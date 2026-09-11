package com.classroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** The body of both POST /refresh and POST /logout - the one thing either action needs. */
@Schema(description = "The refresh token issued at login or by a previous refresh")
public record RefreshTokenRequest(
        @Schema(description = "The refresh token to exchange (POST /refresh) or revoke (POST /logout)",
                example = "kX9f3n2pQs7vLmZaR1tYcW8bE4dH6jN0oU5rT2sI7wA")
        String refreshToken) {
}
