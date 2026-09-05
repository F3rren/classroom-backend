package com.prenotazioni.auth.controller;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.model.Role;
import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.auth.dto.LoginPayload;
import com.prenotazioni.auth.dto.LoginRequest;
import com.prenotazioni.auth.dto.LoginResponse;
import com.prenotazioni.auth.dto.UserSummaryDto;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.auth.service.AuthService;
import com.prenotazioni.auth.service.LoginAttemptLimiter;
import com.prenotazioni.auth.service.JwtService;
import com.prenotazioni.util.LogSanitizer;

import com.prenotazioni.util.Timestamps;

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
import org.springframework.beans.factory.annotation.Value;
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

    // Il conteggio dei tentativi sta in LoginAttemptLimiter e non piu' qui: era un
    // campo static dentro il controller, e la mappa non veniva mai svuotata.
    private final LoginAttemptLimiter attemptLimiter;

    AuthController(AuthService authService, JwtService jwtService, LoginAttemptLimiter attemptLimiter) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.attemptLimiter = attemptLimiter;
    }


    // ==================== UTILITY METHODS ====================
    
    /**
     * L'identificativo della richiesta in corso, non uno nuovo: e' lo stesso che
     * GlobalExceptionHandler mettera' nella risposta e nel log dello stack trace.
     * Prima erano due valori scorrelati e una richiesta fallita compariva nei log
     * sotto due id diversi, uno per il controller e uno per il gestore.
     */
    private String generateSessionId() {
        return RequestCorrelationFilter.corrente();
    }
    
    /**
     * Formatta timestamp in modo consistente
     */
    private String formatTimestamp(LocalDateTime timestamp) {
        return Timestamps.format(timestamp);
    }
    
    /**
     * Crea una risposta di errore standardizzata
     */
    private ApiEnvelope<Void> createErrorResponse(String errorCode, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(errorCode, message, userMessage, sessionId);
    }

    /**
     * Valida il formato dell'email con controlli base
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        String trimmedEmail = email.trim();
        
        // Controlli base più permissivi
        return trimmedEmail.contains("@") && 
               trimmedEmail.contains(".") && 
               trimmedEmail.indexOf("@") > 0 && 
               trimmedEmail.indexOf("@") < trimmedEmail.lastIndexOf(".") &&
               trimmedEmail.lastIndexOf(".") < trimmedEmail.length() - 1;
    }
    
    // ==================== AUTHENTICATION ENDPOINTS ====================

    @PostMapping("/login")
    @Operation(summary = "Login utente")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Login effettuato con successo",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO login - Tentativo di accesso");

        try {
            // Validazione input - email
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                logger.warn("FINE login - Email mancante");
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

            // Rate limiting anti brute-force, per IP + email
            // getRemoteAddr() e' l'indirizzo di chi ha aperto la connessione. Dietro il
            // gateway sarebbe SEMPRE il gateway, e la meta' IP della chiave diventerebbe
            // costante: chiunque potrebbe cosi' esaurire il contatore di un indirizzo altrui
            // e tenerlo fuori dal proprio account. server.forward-headers-strategy=framework,
            // in application.properties, e' cio' che rende questa riga di nuovo vera.
            String rateLimitKey = httpRequest.getRemoteAddr() + "|" + email;
            if (attemptLimiter.troppiTentativi(rateLimitKey)) {
                logger.warn("FINE login - Troppi tentativi di login per: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("TOO_MANY_ATTEMPTS",
                                      "Too many login attempts",
                                      "Hai effettuato troppi tentativi di accesso. Riprova tra qualche minuto.",
                                      sessionId),
                    HttpStatus.TOO_MANY_REQUESTS
                );
            }

            // Validazione formato email
            if (!isValidEmail(email)) {
                logger.warn("FINE login - Formato email non valido: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_EMAIL_FORMAT", 
                                      "Invalid email format", 
                                      "Il formato dell'email inserita non è valido.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Validazione input - password
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                logger.warn("FINE login - Password mancante per email: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("MISSING_PASSWORD", 
                                      "Missing password", 
                                      "La password è obbligatoria per effettuare il login.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Validazione lunghezza password (sicurezza base)
            if (request.getPassword().length() < 3) {
                logger.warn("FINE login - Password troppo corta per email: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("PASSWORD_TOO_SHORT", 
                                      "Password too short", 
                                      "La password deve essere di almeno 3 caratteri.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Tentativo di login
            User utente;
            try {
                utente = authService.login(email, request.getPassword());
            } catch (Exception e) {
                logger.error("FINE login - Errore critico durante autenticazione per email: {} | Errore: {}", maskedEmail, e.getMessage(), e);
                return new ResponseEntity<>(
                    createErrorResponse("AUTHENTICATION_ERROR", 
                                      "Authentication failed unexpectedly", 
                                      "Si è verificato un problema durante l'autenticazione. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            // Controllo credenziali
            if (utente == null) {
                logger.warn("FINE login - Credenziali non valide per email: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_CREDENTIALS", 
                                      "Invalid credentials", 
                                      "Email o password non corretti. Verifica le tue credenziali e riprova.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
            
            // Controllo integrità dati utente
            if (utente.getId() == null) {
                logger.error("FINE login - Utente trovato ma con dati corrotti: {}", maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("USER_DATA_CORRUPTION", 
                                      "Utente record is inconsistent", 
                                      "I dati del tuo account sembrano essere corrotti. Contatta il supporto tecnico.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            // Generazione token JWT
            String token;
            try {
                token = jwtService.generateToken(utente);
                if (token == null || token.trim().isEmpty()) {
                    logger.error("FINE login - Token generato è null o vuoto per utente ID: {}", utente.getId());
                    return new ResponseEntity<>(
                        createErrorResponse("TOKEN_GENERATION_FAILED", 
                                          "Token generation failed", 
                                          "Si è verificato un problema nella generazione del token di accesso. Riprova.", 
                                          sessionId),
                        HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }
            } catch (Exception e) {
                logger.error("FINE login - Errore critico durante generazione token per utente ID: {} | Errore: {}", utente.getId(), e.getMessage(), e);
                return new ResponseEntity<>(
                    createErrorResponse("TOKEN_GENERATION_ERROR", 
                                      "Token generation failed", 
                                      "Si è verificato un problema nella generazione del token di accesso.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            logger.debug("FINE login - Login effettuato con successo | Utente ID: {} | Username: {} | Ruolo: {}", utente.getId(), 
                       utente.getUsername() != null ? utente.getUsername() : "N/A",
                       utente.getRuolo() != null ? utente.getRuolo().getValore() : "USER");
            
            // Preparazione dati di risposta (senza informazioni sensibili)
            LoginPayload authData = new LoginPayload(token, UserSummaryDto.basic(utente), formatTimestamp(LocalDateTime.now()));

            // Response compatibile con il frontend esistente (token duplicato a livello radice)
            LoginResponse response = new LoginResponse("Login effettuato con successo", token, authData, sessionId);
            
            return new ResponseEntity<>(response, HttpStatus.OK);
            
        } catch (Exception e) {
            logger.error("FINE login - Errore critico non gestito: {}", e.getMessage(), e);
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
