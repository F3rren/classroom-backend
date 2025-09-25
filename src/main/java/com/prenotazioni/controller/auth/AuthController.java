package com.prenotazioni.controller.auth;

import com.prenotazioni.model.Utente;
import com.prenotazioni.service.AuthService;
import com.prenotazioni.service.JwtService;

import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

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
    private Map<String, Object> createErrorResponse(String errorCode, String message, String userMessage, String sessionId) {
        return Map.of(
            "success", false,
            "error", errorCode,
            "message", message,
            "userMessage", userMessage,
            "timestamp", formatTimestamp(LocalDateTime.now()),
            "sessionId", sessionId
        );
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
    
    /**
     * Oscura parzialmente l'email per i log di sicurezza
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    // ==================== DTO CLASSES ====================

    public static class LoginRequest {
        private String email;
        private String password;
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // ==================== AUTHENTICATION ENDPOINTS ====================

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO login - Tentativo di accesso", sessionId);
        
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
            String maskedEmail = maskEmail(email);
            
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
            
            logger.info("[{}] FINE login - Login effettuato con successo | Utente ID: {} | Username: {} | Ruolo: {}", 
                       sessionId, utente.getId(), 
                       utente.getUsername() != null ? utente.getUsername() : "N/A",
                       utente.getRuolo() != null ? utente.getRuolo() : "USER");
            
            // Preparazione dati di risposta (senza informazioni sensibili)
            Map<String, Object> authData = Map.of(
                "token", token,
                "user", Map.of(
                    "id", utente.getId(),
                    "username", utente.getUsername() != null ? utente.getUsername() : "",
                    "nome", utente.getNome() != null ? utente.getNome() : "",
                    "email", utente.getEmail() != null ? utente.getEmail() : "",
                    "ruolo", utente.getRuolo() != null ? utente.getRuolo() : "USER"
                ),
                "loginTime", formatTimestamp(LocalDateTime.now()),
                "tokenType", "Bearer"
            );
            
            // Response compatibile con il frontend esistente
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Login effettuato con successo",
                "token", token, // <- Per compatibilità con il frontend
                "data", authData,
                "timestamp", formatTimestamp(LocalDateTime.now()),
                "sessionId", sessionId
            );
            
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
