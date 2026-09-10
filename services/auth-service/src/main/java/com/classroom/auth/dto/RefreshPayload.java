package com.classroom.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * The contents of "data" in the refresh response: a new pair of tokens, nothing else - except
 * for a cookie-session caller (see AuthController.refresh), where "nothing else" is the whole
 * point: the new pair already went out as HttpOnly cookies, and repeating it here in clear
 * text is exactly the exposure an HttpOnly cookie is meant to avoid. That caller already
 * cannot have put a token in the request body - a cookie session has none to put there - so
 * the same signal that says "use the cookie" also says "leave token and refreshToken out of
 * the response".
 *
 * @JsonInclude(NON_NULL): a null token/refreshToken is absent from the JSON, not present as
 * "null" - a caller checking `if (body.token)` would treat either the same, but a network
 * capture or a browser extension inspecting responses should not find the value at all.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Schema(description = "The contents of \"data\" in the refresh response. token and refreshToken "
        + "are absent when the caller used the session cookie: the new pair already went out as "
        + "HttpOnly cookies, and this response has nothing further to add")
public class RefreshPayload {

    @Schema(description = "The new JWT to send in the Authorization header - absent for a cookie-session caller")
    private final String token;
    @Schema(description = "The new refresh token - the one presented to obtain it is no longer valid - "
            + "absent for a cookie-session caller")
    private final String refreshToken;
    @Schema(description = "The authentication scheme to use", example = "Bearer")
    private final String tokenType = "Bearer";

    public RefreshPayload(String token, String refreshToken) {
        this.token = token;
        this.refreshToken = refreshToken;
    }

    /** For a cookie-session caller: the new pair travelled as cookies, not in this body. */
    public static RefreshPayload forCookieSession() {
        return new RefreshPayload(null, null);
    }
}
