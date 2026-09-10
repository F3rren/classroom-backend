package com.classroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * The contents of "data" in the logout response. Always {@code loggedOut=true}: the endpoint
 * is deliberately idempotent (see RefreshTokenService.revoke), so there is nothing this field
 * could say other than "done", whether or not the token it was given still existed.
 */
@Getter
@Schema(description = "The contents of \"data\" in the logout response")
public class LogoutAck {

    @Schema(description = "Always true: logout does not distinguish an unknown or already-used token from a valid one")
    private final boolean loggedOut = true;
}
