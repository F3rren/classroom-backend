package com.prenotazioni.controller.auth;

import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.dto.LoginPayload;
import com.prenotazioni.dto.LoginRequest;
import com.prenotazioni.dto.LoginResponse;
import com.prenotazioni.dto.UserSummaryDto;
import com.prenotazioni.model.Utente;
import com.prenotazioni.service.AuthService;
import com.prenotazioni.service.JwtService;
import com.prenotazioni.util.LogSanitizer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Rate limiting minimo anti brute-force sul login: finestra fissa in memoria, per processo.
    // Non sopravvive a un restart e non e' condiviso tra piu' istanze, ma e' sufficiente per
    // rallentare un attacco a dizionario contro una singola istanza in sviluppo/produzione singola.
    // Configurabile via property cosi' i test possono alzare il limite senza disattivare la protezione.
    @Value("${auth.rate-limit.max-attempts:5}")
    private int maxLoginAttempts;

    @Value("${auth.rate-limit.window-ms:60000}")
    private long rateLimitWindowMs;

    private static final ConcurrentHashMap<String, RateLimitEntry> loginAttempts = new ConcurrentHashMap<>();

    private final AuthService authService;

    private final JwtService jwtService;

    private static class RateLimitEntry {
        int count;
        long windowStart = System.currentTimeMillis();
    }

    AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    private boolean isLoginRateLimited(String key) {
        long now = System.currentTimeMillis();
        RateLimitEntry entry = loginAttempts.computeIfAbsent(key, k -> new RateLimitEntry());
        synchronized (entry) {
            if (now - entry.windowStart > rateLimitWindowMs) {
                entry.windowStart = now;
                entry.count = 0;
            }
            entry.count++;
            return entry.count > maxLoginAttempts;
        }
    }


    // ==================== UTILITY METHODS ====================
    
    /**
     * Genera un ID sessione univoco per il tracking delle operazioni di autenticazione
     */
    private String generateSessionId() {
        return "AUTH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Formatta timestamp in modo consistente
     */
    private String formatTimestamp(LocalDateTime timestamp) {
        return timestamp.format(TIMESTAMP_FORMATTER);
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
        logger.debug("[{}] INIZIO login - Tentativo di accesso", sessionId);

        try {
            // Validazione input - email
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                logger.warn("[{}] FINE login - Email mancante", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("MISSING_EMAIL",
                                      "Email mancante",
                                      "L'email è obbligatoria per effettuare il login.",
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }

            String email = request.getEmail().trim().toLowerCase();
            String maskedEmail = LogSanitizer.maskEmail(email);

            // Rate limiting anti brute-force, per IP + email
            String rateLimitKey = httpRequest.getRemoteAddr() + "|" + email;
            if (isLoginRateLimited(rateLimitKey)) {
                logger.warn("[{}] FINE login - Troppi tentativi di login per: {}", sessionId, maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("TOO_MANY_ATTEMPTS",
                                      "Troppi tentativi di login",
                                      "Hai effettuato troppi tentativi di accesso. Riprova tra qualche minuto.",
                                      sessionId),
                    HttpStatus.TOO_MANY_REQUESTS
                );
            }

            // Validazione formato email
            if (!isValidEmail(email)) {
                logger.warn("[{}] FINE login - Formato email non valido: {}", sessionId, maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_EMAIL_FORMAT", 
                                      "Formato email non valido", 
                                      "Il formato dell'email inserita non è valido.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Validazione input - password
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                logger.warn("[{}] FINE login - Password mancante per email: {}", sessionId, maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("MISSING_PASSWORD", 
                                      "Password mancante", 
                                      "La password è obbligatoria per effettuare il login.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Validazione lunghezza password (sicurezza base)
            if (request.getPassword().length() < 3) {
                logger.warn("[{}] FINE login - Password troppo corta per email: {}", sessionId, maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("PASSWORD_TOO_SHORT", 
                                      "Password troppo corta", 
                                      "La password deve essere di almeno 3 caratteri.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Tentativo di login
            Utente utente;
            try {
                utente = authService.login(email, request.getPassword());
            } catch (Exception e) {
                logger.error("[{}] FINE login - Errore critico durante autenticazione per email: {} | Errore: {}", 
                           sessionId, maskedEmail, e.getMessage(), e);
                return new ResponseEntity<>(
                    createErrorResponse("AUTHENTICATION_ERROR", 
                                      "Errore durante l'autenticazione", 
                                      "Si è verificato un problema durante l'autenticazione. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            // Controllo credenziali
            if (utente == null) {
                logger.warn("[{}] FINE login - Credenziali non valide per email: {}", sessionId, maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_CREDENTIALS", 
                                      "Credenziali non valide", 
                                      "Email o password non corretti. Verifica le tue credenziali e riprova.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
            
            // Controllo integrità dati utente
            if (utente.getId() == null) {
                logger.error("[{}] FINE login - Utente trovato ma con dati corrotti: {}", sessionId, maskedEmail);
                return new ResponseEntity<>(
                    createErrorResponse("USER_DATA_CORRUPTION", 
                                      "Dati utente corrotti", 
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
                    logger.error("[{}] FINE login - Token generato è null o vuoto per utente ID: {}", sessionId, utente.getId());
                    return new ResponseEntity<>(
                        createErrorResponse("TOKEN_GENERATION_FAILED", 
                                          "Errore nella generazione del token", 
                                          "Si è verificato un problema nella generazione del token di accesso. Riprova.", 
                                          sessionId),
                        HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }
            } catch (Exception e) {
                logger.error("[{}] FINE login - Errore critico durante generazione token per utente ID: {} | Errore: {}", 
                           sessionId, utente.getId(), e.getMessage(), e);
                return new ResponseEntity<>(
                    createErrorResponse("TOKEN_GENERATION_ERROR", 
                                      "Errore nella generazione del token", 
                                      "Si è verificato un problema nella generazione del token di accesso.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            logger.debug("[{}] FINE login - Login effettuato con successo | Utente ID: {} | Username: {} | Ruolo: {}", 
                       sessionId, utente.getId(), 
                       utente.getUsername() != null ? utente.getUsername() : "N/A",
                       utente.getRuolo() != null ? utente.getRuolo().getValore() : "USER");
            
            // Preparazione dati di risposta (senza informazioni sensibili)
            LoginPayload authData = new LoginPayload(token, UserSummaryDto.basic(utente), formatTimestamp(LocalDateTime.now()));

            // Response compatibile con il frontend esistente (token duplicato a livello radice)
            LoginResponse response = new LoginResponse("Login effettuato con successo", token, authData, sessionId);
            
            return new ResponseEntity<>(response, HttpStatus.OK);
            
        } catch (Exception e) {
            logger.error("[{}] FINE login - Errore critico non gestito: {}", sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore imprevisto durante il login. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
