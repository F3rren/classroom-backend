package com.prenotazioni.controller;

import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.service.PrenotazioneService;
import com.prenotazioni.service.JwtService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/prenotazioni")
public class PrenotazioneController {
    
    private static final Logger logger = LoggerFactory.getLogger(PrenotazioneController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Autowired
    private PrenotazioneService prenotazioneService;
    
    @Autowired
    private JwtService jwtService;
    
    /**
     * Genera un ID di sessione univoco per il tracking delle operazioni
     */
    private String generateSessionId() {
        return "S" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Formatta un timestamp per i log
     */
    private String formatTimestamp(LocalDateTime dateTime) {
        return dateTime.format(TIMESTAMP_FORMATTER);
    }
    
    /**
     * Crea una risposta di errore standardizzata
     */
    private Map<String, Object> createErrorResponse(String error, String message, String userMessage, String sessionId) {
        return Map.of(
            "success", false,
            "error", error,
            "message", message,
            "userMessage", userMessage,
            "timestamp", formatTimestamp(LocalDateTime.now()),
            "sessionId", sessionId != null ? sessionId : generateSessionId()
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
            "sessionId", sessionId != null ? sessionId : generateSessionId()
        );
    }
    
    // DTO per le richieste di prenotazione
    public static class PrenotazioneRequest {
        private Long aulaId;
        private Long corsoId;
        private String inizio; // formato: "2024-12-25T14:30:00"
        private String fine;
        private String descrizione;
        
        // Getters e Setters
        public Long getAulaId() { return aulaId; }
        public void setAulaId(Long aulaId) { this.aulaId = aulaId; }
        public Long getCorsoId() { return corsoId; }
        public void setCorsoId(Long corsoId) { this.corsoId = corsoId; }
        public String getInizio() { return inizio; }
        public void setInizio(String inizio) { this.inizio = inizio; }
        public String getFine() { return fine; }
        public void setFine(String fine) { this.fine = fine; }
        public String getDescrizione() { return descrizione; }
        public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    }
    
    // Metodo privato per verificare autenticazione (senza controllo ruolo)
    private ResponseEntity<?> checkAuth(String authHeader) 
    {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO checkAuth - Verifica autenticazione utente", sessionId);

        if (authHeader == null) 
        {
            logger.warn("[{}] FINE checkAuth - Token di autorizzazione completamente mancante", sessionId);
            return new ResponseEntity<>
            (
                Map.of(
                    "success", false,
                    "error", "MISSING_AUTH_HEADER",
                    "message", "Token di autorizzazione mancante",
                    "userMessage", "Devi effettuare il login per accedere a questa funzionalità.",
                    "timestamp", LocalDateTime.now().toString(),
                    "sessionId", sessionId
                ),
                HttpStatus.UNAUTHORIZED
            );
        }
        
        if (!authHeader.startsWith("Bearer ")) 
        {
            logger.warn("[{}] FINE checkAuth - Header di autorizzazione malformato: {}", sessionId, authHeader.substring(0, Math.min(20, authHeader.length())));
            return new ResponseEntity<>
            (
                Map.of(
                    "success", false,
                    "error", "MALFORMED_AUTH_HEADER",
                    "message", "Header di autorizzazione malformato",
                    "userMessage", "Il token di autenticazione non è nel formato corretto. Riprova ad effettuare il login.",
                    "timestamp", LocalDateTime.now().toString(),
                    "sessionId", sessionId
                ),
                HttpStatus.UNAUTHORIZED
            );
        }

        logger.info("[{}] Token di autorizzazione presente e correttamente formattato", sessionId);
        String token = authHeader.substring(7);
        
        if (token.trim().isEmpty()) {
            logger.warn("[{}] FINE checkAuth - Token vuoto dopo Bearer", sessionId);
            return new ResponseEntity<>
            (
                Map.of(
                    "success", false,
                    "error", "EMPTY_TOKEN",
                    "message", "Token vuoto",
                    "userMessage", "Token di autenticazione vuoto. Effettua nuovamente il login.",
                    "timestamp", LocalDateTime.now().toString(),
                    "sessionId", sessionId
                ),
                HttpStatus.UNAUTHORIZED
            );
        }

        try 
        {
            if (!jwtService.validateToken(token)) 
            {
                logger.warn("[{}] FINE checkAuth - Token non valido o scaduto", sessionId);
                return new ResponseEntity<>
                (
                    Map.of(
                        "success", false,
                        "error", "INVALID_TOKEN",
                        "message", "Token non valido o scaduto",
                        "userMessage", "La tua sessione è scaduta o il token non è valido. Effettua nuovamente il login.",
                        "timestamp", LocalDateTime.now().toString(),
                        "sessionId", sessionId
                    ),
                    HttpStatus.UNAUTHORIZED
                );
            }
        } catch (Exception e) 
        {
            logger.error("[{}] Errore critico durante la validazione del token: {} | Token length: {} | Exception: {}", 
                        sessionId, e.getMessage(), token.length(), e.getClass().getSimpleName(), e);
            return new ResponseEntity<>
            (
                Map.of(
                    "success", false,
                    "error", "TOKEN_VALIDATION_ERROR",
                    "message", "Errore interno nella validazione del token",
                    "userMessage", "Si è verificato un problema con l'autenticazione. Riprova ad effettuare il login.",
                    "timestamp", LocalDateTime.now().toString(),
                    "sessionId", sessionId
                ),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        logger.info("[{}] FINE checkAuth - Token valido, accesso consentito", sessionId);
        return null; // Access granted
    }
    
    // Prenota un'aula
    @PostMapping("/prenota")
    public ResponseEntity<?> prenotaAula(@RequestBody PrenotazioneRequest request,
                                        @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO prenotaAula - AulaId: {}, CorsoId: {}, Periodo: {} - {}", 
                   sessionId, request.getAulaId(), request.getCorsoId(), request.getInizio(), request.getFine());

        // Validazione preliminare dei dati
        if (request.getAulaId() == null) {
            logger.warn("[{}] FINE prenotaAula - AulaId mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_AULA_ID", "AulaId mancante", 
                                  "Devi specificare quale aula vuoi prenotare.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        // Validazione corso - OPZIONALE per prenotazioni libere
        // Il service gestisce corsoId null per prenotazioni libere
        /*
        if (request.getCorsoId() == null) {
            logger.warn("[{}] FINE prenotaAula - CorsoId mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_CORSO_ID", "CorsoId mancante", 
                                  "Devi specificare per quale corso stai prenotando l'aula.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        */
        
        if (request.getInizio() == null || request.getInizio().trim().isEmpty()) {
            logger.warn("[{}] FINE prenotaAula - Data di inizio mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_START_DATE", "Data di inizio mancante", 
                                  "Devi specificare quando inizia la prenotazione.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (request.getFine() == null || request.getFine().trim().isEmpty()) {
            logger.warn("[{}] FINE prenotaAula - Data di fine mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_END_DATE", "Data di fine mancante", 
                                  "Devi specificare quando finisce la prenotazione.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE prenotaAula - Autenticazione fallita", sessionId);
            return authCheck;
        }

        try {
            String token = authHeader.substring(7);
            Long utenteId = jwtService.getUserIdFromToken(token);
            logger.info("[{}] Utente autenticato con ID: {}", sessionId, utenteId);

            // Parsing delle date con gestione errori dettagliata
            LocalDateTime inizio;
            LocalDateTime fine;
            
            try {
                inizio = LocalDateTime.parse(request.getInizio());
            } catch (DateTimeParseException e) {
                logger.warn("[{}] FINE prenotaAula - Errore parsing data inizio: '{}' | Errore: {}", 
                           sessionId, request.getInizio(), e.getMessage());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_START_DATE", 
                                      "Formato data di inizio non valido", 
                                      "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            try {
                fine = LocalDateTime.parse(request.getFine());
            } catch (DateTimeParseException e) {
                logger.warn("[{}] FINE prenotaAula - Errore parsing data fine: '{}' | Errore: {}", 
                           sessionId, request.getFine(), e.getMessage());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_END_DATE", 
                                      "Formato data di fine non valido", 
                                      "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }

            // Validazione logica delle date
            if (fine.isBefore(inizio)) {
                logger.warn("[{}] FINE prenotaAula - Data fine precedente alla data inizio: {} < {}", 
                           sessionId, formatTimestamp(fine), formatTimestamp(inizio));
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_DATE_RANGE", 
                                      "Range temporale non valido", 
                                      "La data di fine deve essere successiva alla data di inizio.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            if (inizio.isBefore(LocalDateTime.now())) {
                logger.warn("[{}] FINE prenotaAula - Tentativo di prenotazione nel passato: {}", 
                           sessionId, formatTimestamp(inizio));
                return new ResponseEntity<>(
                    createErrorResponse("PAST_DATE", 
                                      "Data nel passato", 
                                      "Non puoi prenotare un'aula per una data già trascorsa.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }

            logger.info("[{}] Validazioni superate, tentativo prenotazione per periodo: {} - {}", 
                        sessionId, formatTimestamp(inizio), formatTimestamp(fine));

            Prenotazione prenotazione = prenotazioneService.prenotaAula(
                request.getAulaId(), request.getCorsoId(), utenteId,
                inizio, fine, request.getDescrizione()
            );

            if (prenotazione == null) {
                logger.warn("[{}] FINE prenotaAula - Prenotazione rifiutata dal servizio - AulaId: {}, Periodo: {} - {}", 
                           sessionId, request.getAulaId(), formatTimestamp(inizio), formatTimestamp(fine));
                return new ResponseEntity<>(
                    createErrorResponse("BOOKING_CONFLICT", 
                                      "Impossibile prenotare l'aula", 
                                      "L'aula non è disponibile nel periodo richiesto. Potrebbe essere già prenotata o fuori servizio.", 
                                      sessionId),
                    HttpStatus.CONFLICT
                );
            }

            logger.info("[{}] FINE prenotaAula - Prenotazione creata con successo - ID: {}, AulaId: {}, UtenteId: {}", 
                       sessionId, prenotazione.getId(), request.getAulaId(), utenteId);
            return new ResponseEntity<>(
                createSuccessResponse("Prenotazione effettuata con successo", 
                                    Map.of("prenotazione", prenotazione, 
                                          "aulaId", request.getAulaId(),
                                          "periodo", formatTimestamp(inizio) + " - " + formatTimestamp(fine)), 
                                    sessionId),
                HttpStatus.CREATED
            );

        } catch (Exception e) {
            logger.error("[{}] FINE prenotaAula - Errore critico imprevisto durante prenotazione | AulaId: {} | Utente: ? | Errore: {}", 
                        sessionId, request.getAulaId(), e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore imprevisto. Se il problema persiste, contatta il supporto tecnico.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Modifica una prenotazione esistente
    @PutMapping("/{prenotazioneId}")
    public ResponseEntity<?> modificaPrenotazione(@PathVariable Long prenotazioneId,
                                                 @RequestBody PrenotazioneRequest request,
                                                 @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO modificaPrenotazione - PrenotazioneId: {}, AulaId: {}, CorsoId: {}, Periodo: {} - {}", 
                   sessionId, prenotazioneId, request.getAulaId(), request.getCorsoId(), request.getInizio(), request.getFine());

        // Validazione preliminare dei dati
        if (request.getAulaId() == null) {
            logger.warn("[{}] FINE modificaPrenotazione - AulaId mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_AULA_ID", "AulaId mancante", 
                                  "Devi specificare quale aula vuoi prenotare.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (request.getInizio() == null || request.getInizio().trim().isEmpty()) {
            logger.warn("[{}] FINE modificaPrenotazione - Data di inizio mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_START_DATE", "Data di inizio mancante", 
                                  "Devi specificare quando inizia la prenotazione.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (request.getFine() == null || request.getFine().trim().isEmpty()) {
            logger.warn("[{}] FINE modificaPrenotazione - Data di fine mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_END_DATE", "Data di fine mancante", 
                                  "Devi specificare quando finisce la prenotazione.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE modificaPrenotazione - Autenticazione fallita", sessionId);
            return authCheck;
        }

        try {
            String token = authHeader.substring(7);
            Long utenteId = jwtService.getUserIdFromToken(token);
            logger.info("[{}] Utente autenticato con ID: {}", sessionId, utenteId);

            // Parsing delle date con gestione errori dettagliata
            LocalDateTime inizio;
            LocalDateTime fine;
            
            try {
                inizio = LocalDateTime.parse(request.getInizio());
            } catch (DateTimeParseException e) {
                logger.warn("[{}] FINE modificaPrenotazione - Errore parsing data inizio: '{}' | Errore: {}", 
                           sessionId, request.getInizio(), e.getMessage());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_START_DATE", 
                                      "Formato data di inizio non valido", 
                                      "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            try {
                fine = LocalDateTime.parse(request.getFine());
            } catch (DateTimeParseException e) {
                logger.warn("[{}] FINE modificaPrenotazione - Errore parsing data fine: '{}' | Errore: {}", 
                           sessionId, request.getFine(), e.getMessage());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_END_DATE", 
                                      "Formato data di fine non valido", 
                                      "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }

            // Validazione logica delle date
            if (fine.isBefore(inizio)) {
                logger.warn("[{}] FINE modificaPrenotazione - Data fine precedente alla data inizio: {} < {}", 
                           sessionId, formatTimestamp(fine), formatTimestamp(inizio));
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_DATE_RANGE", 
                                      "Range temporale non valido", 
                                      "La data di fine deve essere successiva alla data di inizio.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            if (inizio.isBefore(LocalDateTime.now())) {
                logger.warn("[{}] FINE modificaPrenotazione - Tentativo di modifica con data nel passato: {}", 
                           sessionId, formatTimestamp(inizio));
                return new ResponseEntity<>(
                    createErrorResponse("PAST_DATE", 
                                      "Data nel passato", 
                                      "Non puoi modificare una prenotazione per una data già trascorsa.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }

            logger.info("[{}] Validazioni superate, tentativo modifica prenotazione ID {} per periodo: {} - {}", 
                        sessionId, prenotazioneId, formatTimestamp(inizio), formatTimestamp(fine));

            Prenotazione prenotazione = prenotazioneService.updatePrenotazione(
                prenotazioneId, request.getAulaId(), request.getCorsoId(), utenteId,
                inizio, fine, request.getDescrizione()
            );

            if (prenotazione == null) {
                logger.warn("[{}] FINE modificaPrenotazione - Modifica rifiutata dal servizio - PrenotazioneId: {}, AulaId: {}, Periodo: {} - {}", 
                           sessionId, prenotazioneId, request.getAulaId(), formatTimestamp(inizio), formatTimestamp(fine));
                return new ResponseEntity<>(
                    createErrorResponse("UPDATE_FAILED", 
                                      "Impossibile modificare la prenotazione", 
                                      "La prenotazione non può essere modificata. Potrebbe non esistere, non avere i permessi o l'aula non è disponibile nel nuovo periodo.", 
                                      sessionId),
                    HttpStatus.CONFLICT
                );
            }

            logger.info("[{}] FINE modificaPrenotazione - Prenotazione modificata con successo - ID: {}, AulaId: {}, UtenteId: {}", 
                       sessionId, prenotazione.getId(), request.getAulaId(), utenteId);
            return new ResponseEntity<>(
                createSuccessResponse("Prenotazione modificata con successo", 
                                    Map.of("prenotazione", prenotazione, 
                                          "aulaId", request.getAulaId(),
                                          "periodo", formatTimestamp(inizio) + " - " + formatTimestamp(fine)), 
                                    sessionId),
                HttpStatus.OK
            );

        } catch (Exception e) {
            logger.error("[{}] FINE modificaPrenotazione - Errore critico imprevisto durante modifica | PrenotazioneId: {} | AulaId: {} | Utente: ? | Errore: {}", 
                        sessionId, prenotazioneId, request.getAulaId(), e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore imprevisto. Se il problema persiste, contatta il supporto tecnico.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Blocca un'aula (solo admin)
    @PostMapping("/blocca")
    public ResponseEntity<?> bloccaAula(@RequestBody PrenotazioneRequest request,
                                       @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO bloccaAula - AulaId: {}, Periodo: {} - {}", 
                   sessionId, request.getAulaId(), request.getInizio(), request.getFine());
        
        // Validazione preliminare dei dati
        if (request.getAulaId() == null) {
            logger.warn("[{}] FINE bloccaAula - AulaId mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_AULA_ID", "AulaId mancante", 
                                  "Devi specificare quale aula vuoi bloccare.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (request.getInizio() == null || request.getInizio().trim().isEmpty()) {
            logger.warn("[{}] FINE bloccaAula - Data di inizio mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_START_DATE", "Data di inizio mancante", 
                                  "Devi specificare quando inizia il blocco dell'aula.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (request.getFine() == null || request.getFine().trim().isEmpty()) {
            logger.warn("[{}] FINE bloccaAula - Data di fine mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_END_DATE", "Data di fine mancante", 
                                  "Devi specificare quando finisce il blocco dell'aula.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE bloccaAula - Autenticazione fallita", sessionId);
            return authCheck;
        }
        
        try {
            String token = authHeader.substring(7);
            String ruolo = jwtService.getRuoloFromToken(token);
            Long utenteId = jwtService.getUserIdFromToken(token);
            
            logger.info("[{}] Controllo autorizzazione admin - UtenteId: {}, Ruolo: {}", sessionId, utenteId, ruolo);
            
            if (!"admin".equals(ruolo)) {
                logger.warn("[{}] FINE bloccaAula - Accesso negato - UtenteId: {}, Ruolo richiesto: admin, Ruolo attuale: {}", 
                           sessionId, utenteId, ruolo);
                return new ResponseEntity<>(
                    createErrorResponse("ACCESS_DENIED", 
                                      "Accesso negato", 
                                      "Solo gli amministratori possono bloccare le aule. Contatta un amministratore se necessario.", 
                                      sessionId),
                    HttpStatus.FORBIDDEN
                );
            }
            
            logger.info("[{}] Autorizzazione admin confermata per utente: {}", sessionId, utenteId);
            
            // Parsing delle date con gestione errori dettagliata
            LocalDateTime inizio;
            LocalDateTime fine;
            
            try {
                inizio = LocalDateTime.parse(request.getInizio());
            } catch (DateTimeParseException e) {
                logger.warn("[{}] FINE bloccaAula - Errore parsing data inizio: '{}' | Errore: {}", 
                           sessionId, request.getInizio(), e.getMessage());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_START_DATE", 
                                      "Formato data di inizio non valido", 
                                      "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            try {
                fine = LocalDateTime.parse(request.getFine());
            } catch (DateTimeParseException e) {
                logger.warn("[{}] FINE bloccaAula - Errore parsing data fine: '{}' | Errore: {}", 
                           sessionId, request.getFine(), e.getMessage());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_END_DATE", 
                                      "Formato data di fine non valido", 
                                      "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Validazione logica delle date
            if (fine.isBefore(inizio)) {
                logger.warn("[{}] FINE bloccaAula - Data fine precedente alla data inizio: {} < {}", 
                           sessionId, formatTimestamp(fine), formatTimestamp(inizio));
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_DATE_RANGE", 
                                      "Range temporale non valido", 
                                      "La data di fine deve essere successiva alla data di inizio.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            logger.info("[{}] Validazioni superate, tentativo blocco aula per periodo: {} - {}", 
                        sessionId, formatTimestamp(inizio), formatTimestamp(fine));
            
            Prenotazione blocco = prenotazioneService.bloccaAula(
                request.getAulaId(), utenteId, inizio, fine, request.getDescrizione()
            );
            
            if (blocco == null) {
                logger.warn("[{}] FINE bloccaAula - Blocco rifiutato dal servizio - AulaId: {}, Periodo: {} - {}", 
                           sessionId, request.getAulaId(), formatTimestamp(inizio), formatTimestamp(fine));
                return new ResponseEntity<>(
                    createErrorResponse("BLOCK_CONFLICT", 
                                      "Impossibile bloccare l'aula", 
                                      "L'aula non può essere bloccata nel periodo richiesto. Potrebbe essere già occupata.", 
                                      sessionId),
                    HttpStatus.CONFLICT
                );
            }
            
            logger.info("[{}] FINE bloccaAula - Aula bloccata con successo - ID blocco: {}, AulaId: {}, Admin: {}", 
                       sessionId, blocco.getId(), request.getAulaId(), utenteId);
            return new ResponseEntity<>(
                createSuccessResponse("Aula bloccata con successo", 
                                    Map.of("blocco", blocco,
                                          "aulaId", request.getAulaId(),
                                          "periodo", formatTimestamp(inizio) + " - " + formatTimestamp(fine),
                                          "amministratore", utenteId), 
                                    sessionId),
                HttpStatus.CREATED
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE bloccaAula - Errore critico imprevisto durante blocco | AulaId: {} | Errore: {}", 
                        sessionId, request.getAulaId(), e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore imprevisto durante il blocco dell'aula.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Verifica disponibilità aula
    @GetMapping("/disponibilita")
    public ResponseEntity<?> verificaDisponibilita(@RequestParam Long aulaId,
                                                   @RequestParam String inizio,
                                                   @RequestParam String fine) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO verificaDisponibilita - AulaId: {}, Periodo: {} - {}", 
                   sessionId, aulaId, inizio, fine);
        
        // Validazione parametri
        if (aulaId == null) {
            logger.warn("[{}] FINE verificaDisponibilita - AulaId mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_AULA_ID", "AulaId mancante", 
                                  "Devi specificare l'ID dell'aula da verificare.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (inizio == null || inizio.trim().isEmpty()) {
            logger.warn("[{}] FINE verificaDisponibilita - Data di inizio mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_START_DATE", "Data di inizio mancante", 
                                  "Devi specificare la data di inizio per verificare la disponibilità.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (fine == null || fine.trim().isEmpty()) {
            logger.warn("[{}] FINE verificaDisponibilita - Data di fine mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_END_DATE", "Data di fine mancante", 
                                  "Devi specificare la data di fine per verificare la disponibilità.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        try {
            LocalDateTime inizioDateTime;
            LocalDateTime fineDateTime;
            
            try {
                inizioDateTime = LocalDateTime.parse(inizio);
            } catch (DateTimeParseException e) {
                logger.warn("[{}] FINE verificaDisponibilita - Errore parsing data inizio: '{}' | Errore: {}", 
                           sessionId, inizio, e.getMessage());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_START_DATE", 
                                      "Formato data di inizio non valido", 
                                      "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            try {
                fineDateTime = LocalDateTime.parse(fine);
            } catch (DateTimeParseException e) {
                logger.warn("[{}] FINE verificaDisponibilita - Errore parsing data fine: '{}' | Errore: {}", 
                           sessionId, fine, e.getMessage());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_END_DATE", 
                                      "Formato data di fine non valido", 
                                      "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            // Validazione logica delle date
            if (fineDateTime.isBefore(inizioDateTime)) {
                logger.warn("[{}] FINE verificaDisponibilita - Data fine precedente alla data inizio: {} < {}", 
                           sessionId, formatTimestamp(fineDateTime), formatTimestamp(inizioDateTime));
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_DATE_RANGE", 
                                      "Range temporale non valido", 
                                      "La data di fine deve essere successiva alla data di inizio.", 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            logger.info("[{}] Verifica disponibilità per AulaId: {} nel periodo: {} - {}", 
                        sessionId, aulaId, formatTimestamp(inizioDateTime), formatTimestamp(fineDateTime));
            
            boolean disponibile = prenotazioneService.isAulaDisponibile(aulaId, inizioDateTime, fineDateTime);
            
            logger.info("[{}] FINE verificaDisponibilita - AulaId: {}, Disponibile: {}, Periodo: {} - {}", 
                       sessionId, aulaId, disponibile, formatTimestamp(inizioDateTime), formatTimestamp(fineDateTime));
            
            return new ResponseEntity<>(
                createSuccessResponse("Verifica disponibilità completata", 
                                    Map.of("aulaId", aulaId, 
                                          "disponibile", disponibile,
                                          "periodo", formatTimestamp(inizioDateTime) + " - " + formatTimestamp(fineDateTime),
                                          "status", disponibile ? "LIBERA" : "OCCUPATA"), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE verificaDisponibilita - Errore critico imprevisto | AulaId: {} | Errore: {}", 
                        sessionId, aulaId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante la verifica della disponibilità.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Stato attuale di un'aula
    @GetMapping("/stato/{aulaId}")
    public ResponseEntity<?> getStatoAula(@PathVariable Long aulaId) {
        logger.info("INIZIO getStatoAula - AulaId: {}", aulaId);
        
        try {
            String stato = prenotazioneService.getStatoAula(aulaId, LocalDateTime.now());
            
            logger.info("FINE getStatoAula - AulaId: {}, Stato: {}", aulaId, stato);
            return new ResponseEntity<>(
                Map.of("aulaId", aulaId, "stato", stato, "timestamp", LocalDateTime.now()),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE getStatoAula - Errore interno per AulaId: {}, Errore: {}", aulaId, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Lista prenotazioni utente
    // ESCLUDE automaticamente le prenotazioni annullate
    @GetMapping("/mie")
    public ResponseEntity<?> getMiePrenotazioni(@RequestHeader("Authorization") String authHeader) {
        logger.info("INIZIO getMiePrenotazioni");
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.info("FINE getMiePrenotazioni - Autenticazione fallita");
            return authCheck;
        }
        
        String token = authHeader.substring(7);
        Long utenteId = jwtService.getUserIdFromToken(token);
        logger.info("Utente autenticato con ID: {}", utenteId);
        
        List<Prenotazione> tuttePrenotazioni = prenotazioneService.getPrenotazioniUtente(utenteId);
        
        // Filtra le prenotazioni annullate
        List<Prenotazione> prenotazioni = tuttePrenotazioni.stream()
            .filter(p -> !"annullata".equalsIgnoreCase(p.getStato()))
            .collect(java.util.stream.Collectors.toList());
        
        logger.info("FINE getMiePrenotazioni - Prenotazioni attive recuperate per utente: {}, totale: {} (escluse {} annullate)", 
                   utenteId, prenotazioni.size(), tuttePrenotazioni.size() - prenotazioni.size());
        return new ResponseEntity<>(
            Collections.singletonMap("prenotazioni", prenotazioni),
            HttpStatus.OK
        );
    }
    
    // Annulla prenotazione
    @DeleteMapping("/{prenotazioneId}")
    public ResponseEntity<?> annullaPrenotazione(@PathVariable Long prenotazioneId,
                                                @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO annullaPrenotazione - PrenotazioneId: {}", sessionId, prenotazioneId);
        
        // Validazione parametri
        if (prenotazioneId == null) {
            logger.warn("[{}] FINE annullaPrenotazione - PrenotazioneId mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_PRENOTAZIONE_ID", "ID prenotazione mancante", 
                                  "Devi specificare l'ID della prenotazione da annullare.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (prenotazioneId <= 0) {
            logger.warn("[{}] FINE annullaPrenotazione - PrenotazioneId non valido: {}", sessionId, prenotazioneId);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_PRENOTAZIONE_ID", "ID prenotazione non valido", 
                                  "L'ID della prenotazione deve essere un numero positivo.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE annullaPrenotazione - Autenticazione fallita", sessionId);
            return authCheck;
        }
        
        try {
            String token = authHeader.substring(7);
            Long utenteId = jwtService.getUserIdFromToken(token);
            logger.info("[{}] Tentativo di annullamento prenotazione {} da parte dell'utente {}", 
                        sessionId, prenotazioneId, utenteId);
            
            // Prima verifichiamo se la prenotazione esiste
            Prenotazione prenotazioneEsistente = prenotazioneService.getPrenotazioneById(prenotazioneId);
            if (prenotazioneEsistente == null) {
                logger.warn("[{}] FINE annullaPrenotazione - Prenotazione non trovata: ID {}", sessionId, prenotazioneId);
                return new ResponseEntity<>(
                    createErrorResponse("PRENOTAZIONE_NOT_FOUND", 
                                      "Prenotazione non trovata", 
                                      "La prenotazione che stai cercando di annullare non esiste.", 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }
            
            logger.info("[{}] Prenotazione trovata: ID {}, proprietario: {}, stato: {}", 
                        sessionId, prenotazioneId, prenotazioneEsistente.getUtente().getId(), 
                        prenotazioneEsistente.getStato());
            
            boolean annullata = prenotazioneService.annullaPrenotazione(prenotazioneId, utenteId);
            
            if (!annullata) {
                // Determiniamo il motivo specifico del fallimento
                if (!prenotazioneEsistente.getUtente().getId().equals(utenteId)) {
                    logger.warn("[{}] FINE annullaPrenotazione - Tentativo di annullare prenotazione di altro utente | PrenotazioneId: {} | Proprietario: {} | Richiedente: {}", 
                               sessionId, prenotazioneId, prenotazioneEsistente.getUtente().getId(), utenteId);
                    return new ResponseEntity<>(
                        createErrorResponse("ACCESS_DENIED", 
                                          "Accesso negato", 
                                          "Puoi annullare solo le tue prenotazioni.", 
                                          sessionId),
                        HttpStatus.FORBIDDEN
                    );
                }
                
                if (!"prenotata".equalsIgnoreCase(prenotazioneEsistente.getStato())) {
                    logger.warn("[{}] FINE annullaPrenotazione - Tentativo di annullare prenotazione con stato non valido | PrenotazioneId: {} | Stato: {}", 
                               sessionId, prenotazioneId, prenotazioneEsistente.getStato());
                    return new ResponseEntity<>(
                        createErrorResponse("INVALID_STATE", 
                                          "Stato prenotazione non valido", 
                                          "Puoi annullare solo prenotazioni attive. Questa prenotazione è nello stato: " + prenotazioneEsistente.getStato(), 
                                          sessionId),
                        HttpStatus.CONFLICT
                    );
                }
                
                logger.warn("[{}] FINE annullaPrenotazione - Annullamento fallito per motivo sconosciuto | PrenotazioneId: {} | UtenteId: {}", 
                           sessionId, prenotazioneId, utenteId);
                return new ResponseEntity<>(
                    createErrorResponse("CANCELLATION_FAILED", 
                                      "Impossibile annullare la prenotazione", 
                                      "La prenotazione non può essere annullata al momento. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.CONFLICT
                );
            }
            
            logger.info("[{}] FINE annullaPrenotazione - Prenotazione annullata con successo | PrenotazioneId: {} | UtenteId: {}", 
                       sessionId, prenotazioneId, utenteId);
            return new ResponseEntity<>(
                createSuccessResponse("Prenotazione annullata con successo", 
                                    Map.of("prenotazioneId", prenotazioneId,
                                          "utenteId", utenteId,
                                          "dataAnnullamento", formatTimestamp(LocalDateTime.now())), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE annullaPrenotazione - Errore critico imprevisto | PrenotazioneId: {} | Errore: {}", 
                        sessionId, prenotazioneId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante l'annullamento della prenotazione.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // ========== NUOVI ENDPOINT PER GESTIONE PRENOTAZIONI ==========

    // Lista tutte le prenotazioni (semplice) - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    // ESCLUDE automaticamente le prenotazioni annullate
    @GetMapping
    public ResponseEntity<?> getAllPrenotazioni(@RequestHeader("Authorization") String authHeader) {
        logger.info("INIZIO getAllPrenotazioni");
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.info("FINE getAllPrenotazioni - Autenticazione fallita");
            return authCheck;
        }

        logger.info("Autenticazione riuscita, recupero tutte le prenotazioni attive (escluse annullate)");
        List<Prenotazione> tuttePrenotazioni = prenotazioneService.getAllPrenotazioni();
        
        // Filtra le prenotazioni annullate
        List<Prenotazione> prenotazioni = tuttePrenotazioni.stream()
            .filter(p -> !"annullata".equalsIgnoreCase(p.getStato()))
            .collect(java.util.stream.Collectors.toList());
        
        if (prenotazioni.isEmpty()) {
            logger.info("FINE getAllPrenotazioni - Nessuna prenotazione attiva trovata");
            return new ResponseEntity<>(
                Collections.singletonMap("message", "Nessuna prenotazione attiva trovata"),
                HttpStatus.OK
            );
        }

        logger.info("FINE getAllPrenotazioni - Prenotazioni attive recuperate: {} (totale con annullate: {})", 
                   prenotazioni.size(), tuttePrenotazioni.size());
        return new ResponseEntity<>(
            Collections.singletonMap("prenotazioni", prenotazioni),
            HttpStatus.OK
        );
    }

    // Singola prenotazione per ID (semplice) - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/{id}")
    public ResponseEntity<?> getPrenotazioneById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.info("INIZIO getPrenotazioneById - ID Prenotazione: {}", id);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.info("FINE getPrenotazioneById - Autenticazione fallita");
            return authCheck;
        }

        logger.info("Autenticazione riuscita, recupero prenotazione con ID: {}", id);
        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            logger.info("FINE getPrenotazioneById - Prenotazione non trovata con ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Prenotazione non trovata"),
                HttpStatus.NOT_FOUND
            );
        }

        logger.info("FINE getPrenotazioneById - Prenotazione recuperata con successo: ID: {}", prenotazione.getId());
        return new ResponseEntity<>(
            Collections.singletonMap("prenotazione", prenotazione),
            HttpStatus.OK
        );
    }

    // Dettagli completi di una prenotazione specifica - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/{id}/details")
    public ResponseEntity<?> getPrenotazioneDetailsById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.info("INIZIO getPrenotazioneDetailsById - ID Prenotazione: {}", id);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.info("FINE getPrenotazioneDetailsById - Autenticazione fallita");
            return authCheck;
        }

        logger.info("Autenticazione riuscita, recupero dettagli completi per prenotazione con ID: {}", id);
        // Prima verifica se la prenotazione esiste
        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            logger.info("FINE getPrenotazioneDetailsById - Prenotazione non trovata con ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Prenotazione non trovata"),
                HttpStatus.NOT_FOUND
            );
        }

        logger.info("Prenotazione trovata: ID: {}", prenotazione.getId());
        // Ottieni i dettagli completi
        List<Map<String, Object>> dettagliCompleti = prenotazioneService.getPrenotazioneCompleteDetails(id);
        
        logger.info("FINE getPrenotazioneDetailsById - Dettagli completi recuperati con successo, totale dettagli: {}", dettagliCompleti.size());
        return new ResponseEntity<>(
            Map.of(
                "prenotazione", prenotazione,
                "dettagliCompleti", dettagliCompleti,
                "totalDettagli", dettagliCompleti.size()
            ),
            HttpStatus.OK
        );
    }

    // Vista completa di tutte le prenotazioni con dettagli - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/all-details")
    public ResponseEntity<?> getAllPrenotazioniWithDetails(@RequestHeader("Authorization") String authHeader) {
        logger.info("INIZIO getAllPrenotazioniWithDetails");
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.info("FINE getAllPrenotazioniWithDetails - Autenticazione fallita");
            return authCheck;
        }

        logger.info("Autenticazione riuscita, recupero dettagli completi di tutte le prenotazioni");
        List<Map<String, Object>> dettagliCompleti = prenotazioneService.getAllCompleteDetails();
        
        logger.info("FINE getAllPrenotazioniWithDetails - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return new ResponseEntity<>(
            Map.of(
                "prenotazioni", dettagliCompleti,
                "totalPrenotazioni", dettagliCompleti.size()
            ),
            HttpStatus.OK
        );
    }

    // Prenotazioni per stato - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/stato/{stato}")
    public ResponseEntity<?> getPrenotazioniByStato(@PathVariable String stato, @RequestHeader("Authorization") String authHeader) {
        logger.info("INIZIO getPrenotazioniByStato - Stato: {}", stato);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.info("FINE getPrenotazioniByStato - Autenticazione fallita");
            return authCheck;
        }

        logger.info("Autenticazione riuscita, recupero prenotazioni per stato: {}", stato);
        try {
            List<Prenotazione> prenotazioni = prenotazioneService.getPrenotazioniByStato(stato.toLowerCase());
            
            logger.info("FINE getPrenotazioniByStato - Prenotazioni recuperate con successo per stato: {}, totale: {}", stato, prenotazioni.size());
            return new ResponseEntity<>(
                Map.of(
                    "stato", stato,
                    "prenotazioni", prenotazioni,
                    "totalPrenotazioni", prenotazioni.size()
                ),
                HttpStatus.OK
            );
        } catch (IllegalArgumentException e) {
            logger.info("FINE getPrenotazioniByStato - Stato non valido: {}", stato);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Stato non valido. Stati disponibili: PRENOTATA, BLOCCATA, MANUTENZIONE, ANNULLATA"),
                HttpStatus.BAD_REQUEST
            );
        } catch (Exception e) {
            logger.error("FINE getPrenotazioniByStato - Errore interno per stato: {}, Errore: {}", stato, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Prenotazioni future - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/future")
    public ResponseEntity<?> getPrenotazioniFuture(@RequestHeader("Authorization") String authHeader) {
        logger.info("INIZIO getPrenotazioniFuture");
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.info("FINE getPrenotazioniFuture - Autenticazione fallita");
            return authCheck;
        }

        logger.info("Autenticazione riuscita, recupero prenotazioni future");
        List<Prenotazione> prenotazioni = prenotazioneService.getPrenotazioniFuture();
        
        logger.info("FINE getPrenotazioniFuture - Prenotazioni future recuperate con successo, totale: {}", prenotazioni.size());
        return new ResponseEntity<>(
            Map.of(
                "prenotazioni", prenotazioni,
                "totalPrenotazioni", prenotazioni.size()
            ),
            HttpStatus.OK
        );
    }
}
