package com.classroom.auth.controller;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.exception.AuthenticationFailedException;
import com.classroom.exception.InvalidRequestException;
import com.classroom.exception.TooManyRequestsException;
import com.classroom.auth.dto.LoginPayload;
import com.classroom.auth.dto.LoginRequest;
import com.classroom.auth.dto.LoginResponse;
import com.classroom.auth.dto.UserSummaryDto;
import com.classroom.auth.model.User;
import com.classroom.auth.service.AuthService;
import com.classroom.auth.service.LoginAttemptLimiter;
import com.classroom.auth.service.JwtService;
import com.classroom.util.LogSanitizer;

import com.classroom.util.Timestamps;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticazione")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    private final JwtService jwtService;

    // The attempt counting lives in LoginAttemptLimiter and no longer here: it used to be a
    // static field inside the controller, and the map was never emptied.
    private final LoginAttemptLimiter attemptLimiter;

    AuthController(AuthService authService, JwtService jwtService, LoginAttemptLimiter attemptLimiter) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.attemptLimiter = attemptLimiter;
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
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            logger.warn("END login - email missing");
            throw new InvalidRequestException("MISSING_EMAIL", "Missing email",
                    "L'email è obbligatoria per effettuare il login.");
        }

        String email = request.getEmail().trim().toLowerCase();
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

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            logger.warn("END login - password missing for email: {}", maskedEmail);
            throw new InvalidRequestException("MISSING_PASSWORD", "Missing password",
                    "La password è obbligatoria per effettuare il login.");
        }

        // Password length check (basic hardening)
        if (request.getPassword().length() < 3) {
            logger.warn("END login - password too short for email: {}", maskedEmail);
            throw new InvalidRequestException("PASSWORD_TOO_SHORT", "Password too short",
                    "La password deve essere di almeno 3 caratteri.");
        }

        // No try/catch around this call, and that is the point of the rewrite: the one that
        // used to be here caught Exception and answered 500 to everything, including what
        // GlobalExceptionHandler maps properly - a database constraint, for one, which is a
        // 409. A failure here now rises and is answered by the handler, with the stack trace
        // logged once instead of swallowed.
        User user = authService.login(email, request.getPassword());

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

        logger.debug("END login - login succeeded | user ID: {} | username: {} | role: {}", user.getId(),
                   user.getUsername() != null ? user.getUsername() : "N/A",
                   user.getRole() != null ? user.getRole().getValue() : "USER");

        // Building the response payload, with nothing sensitive in it
        LoginPayload authData = new LoginPayload(token, UserSummaryDto.basic(user), formatTimestamp(LocalDateTime.now()));

        // Shape kept for the existing frontend: the token is duplicated at the root
        return new ResponseEntity<>(
                new LoginResponse("Login effettuato con successo", token, authData, sessionId),
                HttpStatus.OK);
    }
}
