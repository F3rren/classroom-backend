package com.classroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** The contents of "data" in the refresh response: a new pair of tokens, nothing else. */
@Getter
@Schema(description = "The contents of \"data\" in the refresh response")
public class RefreshPayload {

    @Schema(description = "The new JWT to send in the Authorization header")
    private final String token;
    @Schema(description = "The new refresh token - the one presented to obtain it is no longer valid")
    private final String refreshToken;
    @Schema(description = "The authentication scheme to use", example = "Bearer")
    private final String tokenType = "Bearer";

    public RefreshPayload(String token, String refreshToken) {
        this.token = token;
        this.refreshToken = refreshToken;
    }
}
