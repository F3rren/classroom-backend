package com.classroom.auth.controller;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.exception.AuthenticationFailedException;
import com.classroom.exception.InvalidRequestException;
import com.classroom.exception.TooManyRequestsException;
import com.classroom.auth.dto.LoginPayload;
import com.classroom.auth.dto.LoginRequest;
import com.classroom.auth.dto.LoginResponse;
import com.classroom.auth.dto.LogoutAck;
import com.classroom.auth.dto.RefreshPayload;
import com.classroom.auth.dto.RefreshTokenRequest;
import com.classroom.auth.dto.UserSummaryDto;
import com.classroom.auth.model.User;
import com.classroom.auth.service.AuthService;
import com.classroom.auth.service.LoginAttemptLimiter;
import com.classroom.auth.service.JwtService;
import com.classroom.auth.service.RefreshTokenService;
import com.classroom.auth.service.UserService;
import com.classroom.dto.ApiEnvelope;
import com.classroom.security.SessionCookies;
import com.classroom.util.LogSanitizer;

import com.classroom.util.Timestamps;

import java.time.Duration;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticazione")
public class AuthController {

    private final AuthService authService;

    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;

    // Only for refresh(): rotating a refresh token yields a userId, and issuing a new access
    // token needs the full User - name, username, role - the same lookup AdminUserController
    // already does before a delete.
    private final UserService userService;

    // The attempt counting lives in LoginAttemptLimiter and no longer here: it used to be a
    // static field inside the controller, and the map was never emptied.
    private final LoginAttemptLimiter attemptLimiter;

    // Le stesse durate con cui i token vengono emessi: un cookie che sopravvive al proprio
    // token lascerebbe il browser a presentare una credenziale gia' morta a ogni richiesta.
    private final long accessTokenTtlMs;

    private final long refreshTokenTtlMs;

    // Normalmente non serve toccarlo: il flag Secure segue gia' il protocollo della
    // richiesta. Serve solo dove il TLS termina altrove e l'inoltro di X-Forwarded-Proto non
    // e' configurato, perche' li' isSecure() direbbe falso anche su HTTPS.
    private final boolean forceSecureCookies;

    AuthController(AuthService authService, JwtService jwtService, RefreshTokenService refreshTokenService,
                   UserService userService, LoginAttemptLimiter attemptLimiter,
                   @Value("${jwt.access-token-expiration-ms:3600000}") long accessTokenTtlMs,
                   @Value("${jwt.refresh-token-expiration-ms:2592000000}") long refreshTokenTtlMs,
                   @Value("${classroom.auth.cookies.force-secure:false}") boolean forceSecureCookies) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
        this.attemptLimiter = attemptLimiter;
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.refreshTokenTtlMs = refreshTokenTtlMs;
        this.forceSecureCookies = forceSecureCookies;
    }


    // ==================== utility methods ====================

    /**
     * The id of the request in flight, not a new one: it is the same one
     * GlobalExceptionHandler will put in the response and in the stack trace log.
     * They used to be two unrelated values, and a failed request appeared in the logs under
     * two different ids, one from the controller and one from the handler.
     */
    private String generateSessionId() {
        return RequestCorrelationFilter.current();
    }

    /** Formats a timestamp the same way everywhere. */
    private String formatTimestamp(LocalDateTime timestamp) {
        return Timestamps.format(timestamp);
    }

    /**
     * Wraps a success payload in the standard envelope - the pattern every OTHER controller
     * in this project already uses (see AdminUserController). login() alone stays on its own
     * bespoke LoginResponse, kept for an existing frontend that already reads that exact
     * shape; refresh() and logout() are new endpoints with nothing legacy to match, so they
     * follow the standard instead of extending the exception.
     */
    private <T> ApiEnvelope<T> createSuccessResponse(String message, T data, String sessionId) {
        return ApiEnvelope.success(message, data, sessionId);
    }

    // ==================== session cookies ====================

    /**
     * Whether the session cookies must carry the Secure flag.
     *
     * It follows the protocol of the request being served, so it is right on its own in both
     * plain HTTP during development and HTTPS in production - provided the proxy forwards
     * X-Forwarded-Proto, which server.forward-headers-strategy=framework already relies on.
     * The property only forces it on where that inference cannot work.
     */
    private boolean useSecureCookies(HttpServletRequest request) {
        return forceSecureCookies || request.isSecure();
    }

    /**
     * Adds the pair of session cookies to a response.
     *
     * The tokens stay in the body as well: a non-browser client keeps working exactly as
     * before, while a browser gains a copy it cannot read from JavaScript.
     */
    private <T> ResponseEntity<T> withSessionCookies(ResponseEntity<T> response, String accessToken,
                                                     String refreshToken, HttpServletRequest request) {
        boolean secure = useSecureCookies(request);

        ResponseCookie access = SessionCookies.build(SessionCookies.ACCESS_TOKEN, accessToken,
                Duration.ofMillis(accessTokenTtlMs), secure);
        ResponseCookie refresh = SessionCookies.build(SessionCookies.REFRESH_TOKEN, refreshToken,
                Duration.ofMillis(refreshTokenTtlMs), secure);

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(SessionCookies.header(), access.toString())
                .header(SessionCookies.header(), refresh.toString())
                .body(response.getBody());
    }

    /**
     * Adds the cookies that delete the session pair.
     *
     * Only the server can do this: an HttpOnly cookie is not removable from the page, which
     * is why a logout that skipped this would leave the browser holding a live session.
     */
    private <T> ResponseEntity<T> withClearedSessionCookies(ResponseEntity<T> response, HttpServletRequest request) {
        boolean secure = useSecureCookies(request);

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(SessionCookies.header(),
                        SessionCookies.expire(SessionCookies.ACCESS_TOKEN, secure).toString())
                .header(SessionCookies.header(),
                        SessionCookies.expire(SessionCookies.REFRESH_TOKEN, secure).toString())
                .body(response.getBody());
    }

    /**
     * The refresh token presented by the caller: from the request body, or from the session
     * cookie when the body does not carry it.
     *
     * A browser using the cookie flow has nothing to put in the body - the token is HttpOnly
     * and the page cannot read it - so requiring it there would make refresh and logout
     * unreachable for exactly the clients the cookies are meant to protect.
     */
    private String presentedRefreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        if (!bodyCarriesRefreshToken(request)) {
            return SessionCookies.read(httpRequest, SessionCookies.REFRESH_TOKEN);
        }
        return request.refreshToken();
    }

    /**
     * Whether the caller put a refresh token in the request body - as opposed to relying on
     * the session cookie, which is what {@link #presentedRefreshToken} falls back to.
     *
     * The SAME check as presentedRefreshToken's own first branch, pulled out so refresh() can
     * ask it too: a caller with nothing in the body is, by construction, a cookie-session
     * caller (there is nowhere else the token could have come from), which is what tells
     * refresh() to leave the new pair out of the response body - see RefreshPayload.
     */
    private boolean bodyCarriesRefreshToken(RefreshTokenRequest request) {
        return request != null && request.refreshToken() != null && !request.refreshToken().isBlank();
    }

    /** Checks the shape of an email address, with basic checks only. */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        String trimmedEmail = email.trim();
        
        // Deliberately permissive: this is a shape check, not an address validator.
        return trimmedEmail.contains("@") && 
               trimmedEmail.contains(".") && 
               trimmedEmail.indexOf("@") > 0 && 
               trimmedEmail.indexOf("@") < trimmedEmail.lastIndexOf(".") &&
               trimmedEmail.lastIndexOf(".") < trimmedEmail.length() - 1;
    }
    
    // ==================== authentication endpoints ====================

    @PostMapping("/login")
    @Operation(summary = "User login")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Login succeeded",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String sessionId = generateSessionId();
        logger.debug("START login - access attempt");

        // The email is read FIRST because it is half the rate-limit key, not because
        // validation comes first: everything else below is deliberately checked after the
        // limiter. See LoginRequest's javadoc - @Valid on the parameter would move all of it
        // in front, and an attacker could then fail validation for ever without ever
        // consuming an attempt.
        if (request.email() == null || request.email().trim().isEmpty()) {
            logger.warn("END login - email missing");
            throw new InvalidRequestException("MISSING_EMAIL", "Missing email",
                    "L'email è obbligatoria per effettuare il login.");
        }

        String email = request.email().trim().toLowerCase();
        String maskedEmail = LogSanitizer.maskEmail(email);

        // Anti brute-force rate limiting, keyed on IP + email.
        // getRemoteAddr() is the address of whoever opened the connection. Behind the
        // gateway that would ALWAYS be the gateway, and the IP half of the key would
        // become a constant: anybody could then exhaust the counter of somebody else's
        // address and lock them out of their own account.
        // server.forward-headers-strategy=framework, in application.properties, is what
        // makes this line true again - and CallerAddressTest in the gateway is what
        // stops the header itself from becoming attacker-controlled.
        String rateLimitKey = httpRequest.getRemoteAddr() + "|" + email;
        if (attemptLimiter.tooManyAttempts(rateLimitKey)) {
            long retryAfter = attemptLimiter.retryAfterSeconds(rateLimitKey);
            logger.warn("END login - too many attempts for: {}, retry in {}s", maskedEmail, retryAfter);
            throw new TooManyRequestsException("TOO_MANY_ATTEMPTS", "Too many login attempts",
                    "Hai effettuato troppi tentativi di accesso. Riprova tra qualche minuto.", retryAfter);
        }

        if (!isValidEmail(email)) {
            logger.warn("END login - invalid email format: {}", maskedEmail);
            throw new InvalidRequestException("INVALID_EMAIL_FORMAT", "Invalid email format",
                    "Il formato dell'email inserita non è valido.");
        }

        if (request.password() == null || request.password().isEmpty()) {
            logger.warn("END login - password missing for email: {}", maskedEmail);
            throw new InvalidRequestException("MISSING_PASSWORD", "Missing password",
                    "La password è obbligatoria per effettuare il login.");
        }

        // Password length check (basic hardening)
        if (request.password().length() < 3) {
            logger.warn("END login - password too short for email: {}", maskedEmail);
            throw new InvalidRequestException("PASSWORD_TOO_SHORT", "Password too short",
                    "La password deve essere di almeno 3 caratteri.");
        }

        // No try/catch around this call, and that is the point of the rewrite: the one that
        // used to be here caught Exception and answered 500 to everything, including what
        // GlobalExceptionHandler maps properly - a database constraint, for one, which is a
        // 409. A failure here now rises and is answered by the handler, with the stack trace
        // logged once instead of swallowed.
        User user = authService.login(email, request.password());

        if (user == null) {
            logger.warn("END login - invalid credentials for email: {}", maskedEmail);
            throw new AuthenticationFailedException("INVALID_CREDENTIALS", "Invalid credentials",
                    "Email o password non corretti. Verifica le tue credenziali e riprova.");
        }

        // The two checks below are against states the database cannot produce - the id is a
        // primary key, and compact() never returns an empty string. They stay because they
        // guard the moment a token is handed out, and they are IllegalStateException on
        // purpose: that is this codebase's way of saying "a defect of ours", and the handler
        // answers 500 without dressing it up as the caller's fault.
        if (user.getId() == null) {
            throw new IllegalStateException(
                    "Stored user has no id, refusing to issue a token for it: " + maskedEmail);
        }

        String token = jwtService.generateToken(user);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Token generation produced nothing for userId=" + user.getId());
        }

        // Issued alongside the access token, not instead of it: without this, the client has
        // no way to ever call POST /refresh or POST /logout - see LoginPayload.
        String refreshToken = refreshTokenService.issue(user.getId());

        logger.debug("END login - login succeeded | user ID: {} | username: {} | role: {}", user.getId(),
                   user.getUsername() != null ? user.getUsername() : "N/A",
                   user.getRole() != null ? user.getRole().getValue() : "USER");

        // A caller that asked for the cookie-only response already gets both tokens as
        // HttpOnly cookies below; leaving them in the body too would be exactly the exposure
        // those cookies exist to prevent. See SessionCookies.AUTH_MODE_HEADER and LoginPayload.
        boolean cookieOnly = SessionCookies.requestsCookieMode(httpRequest);
        String bodyToken = cookieOnly ? null : token;
        String bodyRefreshToken = cookieOnly ? null : refreshToken;

        // Building the response payload, with nothing sensitive in it
        LoginPayload authData = new LoginPayload(bodyToken, bodyRefreshToken, UserSummaryDto.basic(user),
                formatTimestamp(LocalDateTime.now()));

        // Shape kept for the existing frontend: the token is duplicated at the root
        ResponseEntity<LoginResponse> response = new ResponseEntity<>(
                new LoginResponse("Login effettuato con successo", bodyToken, authData, sessionId),
                HttpStatus.OK);

        // The same pair, also as HttpOnly cookies: a browser can then hold the session
        // without the page ever being able to read it.
        return withSessionCookies(response, token, refreshToken, httpRequest);
    }

    /**
     * Exchanges a refresh token for a new access token (and a new refresh token: see
     * RefreshTokenService.rotate - the one presented here stops working the moment this call
     * succeeds).
     *
     * Public, like login: whoever calls this by definition might not be holding a valid
     * access token any more (that is the whole point of refreshing), so it cannot require
     * one.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    @SecurityRequirements
    public ResponseEntity<ApiEnvelope<RefreshPayload>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String sessionId = generateSessionId();
        logger.debug("START refresh");

        // The body is no longer required: a browser on the cookie flow cannot fill it in.
        String presented = presentedRefreshToken(request, httpRequest);
        if (presented == null) {
            logger.warn("END refresh - refresh token missing");
            throw new InvalidRequestException("MISSING_REFRESH_TOKEN", "Missing refresh token",
                    "Il token di aggiornamento e' obbligatorio.");
        }

        // rotate() throws AuthenticationFailedException for anything not found, expired or
        // already used - nothing more specific is asked of it here on purpose (see its own
        // javadoc on why the three cases are not told apart).
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(presented);

        User user = userService.findById(rotation.userId());
        if (user == null) {
            // The foreign key (ON DELETE CASCADE, see V4__refresh_tokens.sql) means a
            // deleted user's tokens are deleted with them - rotate() could not have returned
            // this userId if the row were gone. Reaching here would mean that guarantee
            // broke, which is this codebase's own defect, not the caller's.
            throw new IllegalStateException(
                    "Refresh token rotation pointed at a user that no longer exists: userId=" + rotation.userId());
        }

        String newAccessToken = jwtService.generateToken(user);

        // A caller with nothing in the request body got here on the cookie alone, so the new
        // pair goes out ONLY as cookies below - repeating it here in clear text would be
        // exactly the exposure the HttpOnly flag is meant to prevent. See RefreshPayload.
        RefreshPayload payload = bodyCarriesRefreshToken(request)
                ? new RefreshPayload(newAccessToken, rotation.refreshToken())
                : RefreshPayload.forCookieSession();

        logger.debug("END refresh - new access token issued | userId: {}", user.getId());
        ResponseEntity<ApiEnvelope<RefreshPayload>> response = new ResponseEntity<>(
                createSuccessResponse("Token aggiornato con successo", payload, sessionId),
                HttpStatus.OK);

        // The rotation invalidated the token the browser was holding: without replacing both
        // cookies here, the next refresh would present the consumed one and end the session.
        return withSessionCookies(response, newAccessToken, rotation.refreshToken(), httpRequest);
    }

    /**
     * Revokes a refresh token, ending that session early. Always succeeds - see
     * RefreshTokenService.revoke on why a missing, unknown or already-revoked token is not an
     * error here.
     *
     * Public, like login and refresh: it takes the refresh token itself as its only proof,
     * not a still-valid access token, since the two can legitimately go out of sync (the
     * access token can still be live for up to an hour after this call, per this feature's
     * documented trade-off - see the plan/README on instant access-token revocation).
     */
    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token, ending that session")
    @SecurityRequirements
    public ResponseEntity<ApiEnvelope<LogoutAck>> logout(
            @RequestBody(required = false) RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String sessionId = generateSessionId();
        logger.debug("START logout");

        String presented = presentedRefreshToken(request, httpRequest);
        if (presented != null) {
            refreshTokenService.revoke(presented);
        }

        logger.debug("END logout");
        ResponseEntity<ApiEnvelope<LogoutAck>> response = new ResponseEntity<>(
                createSuccessResponse("Logout effettuato con successo", new LogoutAck(), sessionId),
                HttpStatus.OK);

        // Deleting the cookies is the server's job: the page cannot remove an HttpOnly
        // cookie, so a logout that skipped this would leave the browser logged in.
        return withClearedSessionCookies(response, httpRequest);
    }
}
