package com.prenotazioni.controller;

import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IUtenteRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {
    
    private static final Logger logger = LoggerFactory.getLogger(MeController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Autowired
    private IUtenteRepository utenteRepository;
  
    // ==================== UTILITY METHODS ====================
    
    /**
     * Genera un ID sessione univoco per il tracking delle operazioni
     */
    private String generateSessionId() {
        return "ME_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
     * Crea una risposta di successo standardizzata
     */
    private Map<String, Object> createSuccessResponse(String message, Object data, String sessionId) {
        return Map.of(
            "success", true,
            "message", message,
            "data", data,
            "timestamp", formatTimestamp(LocalDateTime.now()),
            "sessionId", sessionId
        );
    }

    // ==================== ENDPOINTS ====================

    @GetMapping
    public ResponseEntity<?> getMe(Authentication authentication) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getMe - Richiesta informazioni profilo utente", sessionId);
        
        try {
            // Validazione autenticazione
            if (authentication == null) {
                logger.error("[{}] FINE getMe - Authentication object è null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("AUTHENTICATION_NULL", 
                                      "Oggetto autenticazione nullo", 
                                      "Errore nell'autenticazione. Effettua nuovamente il login.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
            
            Object principal = authentication.getPrincipal();
            if (principal == null) {
                logger.error("[{}] FINE getMe - Principal è null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("PRINCIPAL_NULL", 
                                      "Principal nullo", 
                                      "Informazioni utente non disponibili. Effettua nuovamente il login.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
            
            String email;
            try {
                email = (String) principal;
            } catch (ClassCastException e) {
                logger.error("[{}] FINE getMe - Principal non è una stringa: {} | Tipo: {}", 
                           sessionId, principal, principal.getClass().getSimpleName());
                return new ResponseEntity<>(
                    createErrorResponse("PRINCIPAL_TYPE_ERROR", 
                                      "Tipo principal non valido", 
                                      "Formato delle informazioni utente non valido. Effettua nuovamente il login.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
            
            // Validazione email
            if (email == null || email.trim().isEmpty()) {
                logger.warn("[{}] FINE getMe - Email vuota o null dal principal", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("EMPTY_EMAIL", 
                                      "Email vuota", 
                                      "Email utente non disponibile. Effettua nuovamente il login.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
            
            email = email.trim().toLowerCase(); // Normalizzazione email
            logger.info("[{}] Email estratta dal principal: {}", sessionId, email);
            
            // Validazione formato email base
            if (!email.contains("@") || !email.contains(".")) {
                logger.warn("[{}] FINE getMe - Formato email non valido: {}", sessionId, email);
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_EMAIL_FORMAT", 
                                      "Formato email non valido", 
                                      "Il formato dell'email non è valido. Effettua nuovamente il login.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            logger.info("[{}] Ricerca utente nel database per email: {}", sessionId, email);
            
            // Ricerca utente nel database
            Utente utente;
            try {
                utente = utenteRepository.findByEmail(email);
            } catch (Exception e) {
                logger.error("[{}] FINE getMe - Errore durante la ricerca utente per email {}: {}", 
                           sessionId, email, e.getMessage(), e);
                return new ResponseEntity<>(
                    createErrorResponse("DATABASE_ERROR", 
                                      "Errore nel database", 
                                      "Si è verificato un problema durante il recupero dei tuoi dati. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            // Controllo esistenza utente
            if (utente == null) {
                logger.warn("[{}] FINE getMe - Utente non trovato nel database per email: {}", sessionId, email);
                return new ResponseEntity<>(
                    createErrorResponse("USER_NOT_FOUND", 
                                      "Utente non trovato", 
                                      String.format("Nessun utente trovato con l'email %s. Verifica le tue credenziali.", email), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }
            
            // Validazione integrità dati utente
            if (utente.getId() == null) {
                logger.error("[{}] FINE getMe - Utente trovato ma con ID null: email={}", sessionId, email);
                return new ResponseEntity<>(
                    createErrorResponse("USER_DATA_CORRUPTION", 
                                      "Dati utente corrotti", 
                                      "I tuoi dati utente sembrano essere corrotti. Contatta il supporto tecnico.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            logger.info("[{}] FINE getMe - Profilo utente recuperato con successo | ID: {} | Nome: {} | Email: {}", 
                       sessionId, utente.getId(), 
                       utente.getNome() != null ? utente.getNome() : "N/A", email);
            
            // Preparazione dati risposta (senza password per sicurezza)
            Map<String, Object> userData = Map.of(
                "id", utente.getId(),
                "username", utente.getUsername() != null ? utente.getUsername() : "",
                "nome", utente.getNome() != null ? utente.getNome() : "",
                "email", utente.getEmail() != null ? utente.getEmail() : "",
                "ruolo", utente.getRuolo() != null ? utente.getRuolo() : "USER",
                "dataRegistrazione", utente.getDataRegistrazione() != null ? 
                    utente.getDataRegistrazione().format(TIMESTAMP_FORMATTER) : null,
                "ultimoAccesso", utente.getUltimoAccesso() != null ?
                    utente.getUltimoAccesso().format(TIMESTAMP_FORMATTER) : 
                    formatTimestamp(LocalDateTime.now())
            );
            
            return new ResponseEntity<>(
                createSuccessResponse("Profilo utente recuperato con successo", userData, sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getMe - Errore critico non gestito: {}", sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore imprevisto durante il recupero del profilo utente.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
