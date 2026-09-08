package com.classroom.auth.controller;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.dto.ApiEnvelope;
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
import org.springframework.http.HttpHeaders;
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
    
    /** Builds an error response in the shape every endpoint uses. */
    private ApiEnvelope<Void> createErrorResponse(String errorCode, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(errorCode, message, userMessage, sessionId);
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
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String sessionId = generateSessionId();
        logger.debug("START login - access attempt");

        try {
            // Input validation - email
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                logger.warn("END login - email missing");
                return new ResponseEntity<>(
                    createErrorResponse("MISSING_EMAIL",
                                      "Missing email",
                                      "L'email è obbligatoria per effettuare il login.",
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
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
                // Retry-After is what makes the refusal actionable: the sentence below says
                // "tra qualche minuto" to a person, this says how many seconds to a client,
                // which would otherwise have to guess and would usually guess too soon.
                // RFC 9110 §10.2.3.
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                    .body(createErrorResponse("TOO_MANY_ATTEMPTS",
                                      "Too many login attempts",
                                      "Hai effettuato troppi tentativi di accesso. Riprova tra qualche minuto.",
                                      sessionId));
            }

            // Email shape validation
            if (!isValidEmail(email)) {
                logger.warn("END login - invalid email format: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_EMAIL_FORMAT", 
                                      "Invalid email format", 
                                      "Il formato dell'email inserita non è valido.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Input validation - password
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                logger.warn("END login - password missing for email: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("MISSING_PASSWORD", 
                                      "Missing password", 
                                      "La password è obbligatoria per effettuare il login.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Password length check (basic hardening)
            if (request.getPassword().length() < 3) {
                logger.warn("END login - password too short for email: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("PASSWORD_TOO_SHORT", 
                                      "Password too short", 
                                      "La password deve essere di almeno 3 caratteri.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // The login attempt itself
            User user;
            try {
                user = authService.login(email, request.getPassword());
            } catch (Exception e) {
                logger.error("END login - critical failure during authentication for email: {} | error: {}", maskedEmail, e.getMessage(), e);
                return new ResponseEntity<>(
                    createErrorResponse("AUTHENTICATION_ERROR", 
                                      "Authentication failed unexpectedly", 
                                      "Si è verificato un problema durante l'autenticazione. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            // Credential check
            if (user == null) {
                logger.warn("END login - invalid credentials for email: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_CREDENTIALS", 
                                      "Invalid credentials", 
                                      "Email o password non corretti. Verifica le tue credenziali e riprova.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
            
            // Sanity check on the stored user
            if (user.getId() == null) {
                logger.error("END login - user found but its stored data is inconsistent: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("USER_DATA_CORRUPTION", 
                                      "Utente record is inconsistent", 
                                      "I dati del tuo account sembrano essere corrotti. Contatta il supporto tecnico.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            // JWT generation
            String token;
            try {
                token = jwtService.generateToken(user);
                if (token == null || token.trim().isEmpty()) {
                    logger.error("END login - the generated token is null or empty for user ID: {}", user.getId());
                    return new ResponseEntity<>(
                        createErrorResponse("TOKEN_GENERATION_FAILED", 
                                          "Token generation failed", 
                                          "Si è verificato un problema nella generazione del token di accesso. Riprova.", 
                                          sessionId),
                        HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }
            } catch (Exception e) {
                logger.error("END login - critical failure generating the token for user ID: {} | error: {}", user.getId(), e.getMessage(), e);
                return new ResponseEntity<>(
                    createErrorResponse("TOKEN_GENERATION_ERROR", 
                                      "Token generation failed", 
                                      "Si è verificato un problema nella generazione del token di accesso.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            logger.debug("END login - login succeeded | user ID: {} | username: {} | role: {}", user.getId(), 
                       user.getUsername() != null ? user.getUsername() : "N/A",
                       user.getRole() != null ? user.getRole().getValue() : "USER");
            
            // Building the response payload, with nothing sensitive in it
            LoginPayload authData = new LoginPayload(token, UserSummaryDto.basic(user), formatTimestamp(LocalDateTime.now()));

            // Shape kept for the existing frontend: the token is duplicated at the root
            LoginResponse response = new LoginResponse("Login effettuato con successo", token, authData, sessionId);
            
            return new ResponseEntity<>(response, HttpStatus.OK);
            
        } catch (Exception e) {
            logger.error("END login - unhandled critical failure: {}", e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Unhandled internal error", 
                                  "Si è verificato un errore imprevisto durante il login. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
