package com.prenotazioni.controller.admin;

import com.prenotazioni.service.AuthService;
import com.prenotazioni.service.JwtService;
import com.prenotazioni.service.AulaService;
import com.prenotazioni.service.PrenotazioneService;
import com.prenotazioni.dto.RegisterRequest;
import com.prenotazioni.dto.AulaRequest;
import com.prenotazioni.model.Utente;
import com.prenotazioni.model.Aula;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AuthService authService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AulaService aulaService;
    @Autowired
    private PrenotazioneService prenotazioneService;

    // ==================== UTILITY METHODS ====================
    
    /**
     * Genera un ID sessione univoco per il tracking delle operazioni admin
     */
    private String generateSessionId() {
        return "ADM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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

    // ==================== ADMIN ACCESS CONTROL ====================
    
    // Metodo privato per verificare se l'utente è admin
    private ResponseEntity<?> checkAdminAccess(String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO checkAdminAccess - Verifica privilegi amministratore", sessionId);
        
        // Validazione header Authorization
        if (authHeader == null || authHeader.trim().isEmpty()) {
            logger.warn("[{}] FINE checkAdminAccess - Header Authorization mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_AUTH_HEADER", 
                                  "Header Authorization mancante", 
                                  "Token di autorizzazione richiesto. Effettua il login come amministratore.", 
                                  sessionId),
                HttpStatus.UNAUTHORIZED
            );
        }

        if (!authHeader.startsWith("Bearer ")) {
            logger.warn("[{}] FINE checkAdminAccess - Formato header Authorization non valido: {}", sessionId, authHeader.substring(0, Math.min(20, authHeader.length())));
            return new ResponseEntity<>(
                createErrorResponse("INVALID_AUTH_FORMAT", 
                                  "Formato Authorization non valido", 
                                  "Il token deve essere in formato Bearer. Effettua nuovamente il login.", 
                                  sessionId),
                HttpStatus.UNAUTHORIZED
            );
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            logger.warn("[{}] FINE checkAdminAccess - Token vuoto", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("EMPTY_TOKEN", 
                                  "Token vuoto", 
                                  "Token di autenticazione vuoto. Effettua nuovamente il login.", 
                                  sessionId),
                HttpStatus.UNAUTHORIZED
            );
        }

        logger.info("[{}] Validazione token JWT", sessionId);
        try {
            if (!jwtService.validateToken(token)) {
                logger.warn("[{}] FINE checkAdminAccess - Token non valido o scaduto", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_TOKEN", 
                                      "Token non valido o scaduto", 
                                      "La tua sessione è scaduta. Effettua nuovamente il login come amministratore.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
        } catch (Exception e) {
            logger.error("[{}] FINE checkAdminAccess - Errore durante validazione token: {}", sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("TOKEN_VALIDATION_ERROR", 
                                  "Errore nella validazione del token", 
                                  "Si è verificato un problema con l'autenticazione. Riprova ad effettuare il login.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        // Controllo ruolo admin
        try {
            String ruolo = jwtService.getRuoloFromToken(token);
            if (ruolo == null || ruolo.trim().isEmpty()) {
                logger.warn("[{}] FINE checkAdminAccess - Ruolo non trovato nel token", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("MISSING_ROLE", 
                                      "Ruolo non trovato", 
                                      "Informazioni sui privilegi non disponibili. Effettua nuovamente il login.", 
                                      sessionId),
                    HttpStatus.UNAUTHORIZED
                );
            }
            
            if (!"admin".equals(ruolo.toLowerCase().trim())) {
                logger.warn("[{}] FINE checkAdminAccess - Accesso negato - Ruolo: {}", sessionId, ruolo);
                return new ResponseEntity<>(
                    createErrorResponse("INSUFFICIENT_PRIVILEGES", 
                                      "Privilegi insufficienti", 
                                      "Accesso negato: sono richiesti privilegi di amministratore per questa operazione.", 
                                      sessionId),
                    HttpStatus.FORBIDDEN
                );
            }
        } catch (Exception e) {
            logger.error("[{}] FINE checkAdminAccess - Errore durante estrazione ruolo: {}", sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("ROLE_EXTRACTION_ERROR", 
                                  "Errore nell'estrazione del ruolo", 
                                  "Si è verificato un problema nel controllo dei privilegi. Riprova ad effettuare il login.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        logger.info("[{}] FINE checkAdminAccess - Accesso amministratore consentito", sessionId);
        return null; // Access granted
    }

    // ==================== USER MANAGEMENT ENDPOINTS ====================
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO register - Registrazione utente da admin | Email: {} | Username: {} | Ruolo: {}", 
                   sessionId, request.getEmail(), request.getUsername(), request.getRuolo());
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE register - Accesso admin negato", sessionId);
            return accessCheck;
        }

        // Validazione input
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            logger.warn("[{}] FINE register - Email mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_EMAIL", 
                                  "Email mancante", 
                                  "L'email è obbligatoria per la registrazione.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            logger.warn("[{}] FINE register - Username mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_USERNAME", 
                                  "Username mancante", 
                                  "Lo username è obbligatorio per la registrazione.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Validazioni superate, tentativo di registrazione utente", sessionId);
        
        try {
            Utente utente = authService.register(request);
            
            if (utente == null) {
                logger.warn("[{}] FINE register - Registrazione fallita (email/username esistenti) - Email: {} | Username: {}", 
                           sessionId, request.getEmail(), request.getUsername());
                return new ResponseEntity<>(
                    createErrorResponse("USER_ALREADY_EXISTS", 
                                      "Email o username già esistenti", 
                                      String.format("Un utente con email %s o username %s esiste già. Usa credenziali diverse.", 
                                                   request.getEmail(), request.getUsername()), 
                                      sessionId),
                    HttpStatus.CONFLICT
                );
            }
            
            logger.info("[{}] FINE register - Utente registrato con successo | ID: {} | Email: {} | Username: {}", 
                       sessionId, utente.getId(), utente.getEmail(), utente.getUsername());
            
            Map<String, Object> responseData = Map.of(
                "userId", utente.getId(),
                "email", utente.getEmail(),
                "username", utente.getUsername(),
                "ruolo", utente.getRuolo() != null ? utente.getRuolo() : "USER"
            );
            
            return new ResponseEntity<>(
                createSuccessResponse("Utente registrato con successo dall'amministratore", responseData, sessionId),
                HttpStatus.CREATED
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE register - Errore critico durante registrazione | Email: {} | Errore: {}", 
                        sessionId, request.getEmail(), e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante la registrazione dell'utente. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Lista tutti gli utenti
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getAllUsers - Richiesta lista completa utenti", sessionId);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE getAllUsers - Accesso admin negato", sessionId);
            return accessCheck;
        }

        logger.info("[{}] Accesso admin confermato, recupero tutti gli utenti", sessionId);
        
        try {
            List<Utente> users = authService.getAllUsers();
            
            if (users == null) {
                logger.error("[{}] FINE getAllUsers - Servizio ha restituito null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio utenti", 
                                      "Si è verificato un problema nel recupero degli utenti. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (users.isEmpty()) {
                logger.info("[{}] FINE getAllUsers - Nessun utente trovato nel sistema", sessionId);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessun utente presente nel sistema", 
                                        Map.of("users", Collections.emptyList(), 
                                              "totalUsers", 0), 
                                        sessionId),
                    HttpStatus.OK
                );
            }

            // Rimozione password per sicurezza
            List<Map<String, Object>> safeUsers = users.stream()
                .map(user -> {
                    Map<String, Object> userMap = Map.of(
                        "id", (Object) user.getId(),
                        "username", (Object) (user.getUsername() != null ? user.getUsername() : ""),
                        "nome", (Object) (user.getNome() != null ? user.getNome() : ""),
                        "email", (Object) (user.getEmail() != null ? user.getEmail() : ""),
                        "ruolo", (Object) (user.getRuolo() != null ? user.getRuolo() : "USER"),
                        "dataRegistrazione", (Object) (user.getDataRegistrazione() != null ? 
                            user.getDataRegistrazione().format(TIMESTAMP_FORMATTER) : ""),
                        "ultimoAccesso", (Object) (user.getUltimoAccesso() != null ? 
                            user.getUltimoAccesso().format(TIMESTAMP_FORMATTER) : "")
                    );
                    return userMap;
                })
                .collect(Collectors.toList());

            logger.info("[{}] FINE getAllUsers - Utenti recuperati con successo, totale: {}", sessionId, users.size());
            return new ResponseEntity<>(
                createSuccessResponse("Lista utenti recuperata con successo", 
                                    Map.of("users", safeUsers, 
                                          "totalUsers", users.size()), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getAllUsers - Errore critico durante recupero utenti: {}", sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero degli utenti.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Modifica utente
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUtente(@PathVariable Long id, @RequestBody RegisterRequest request, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO updateUtente - ID Utente: {} | Email: {} | Username: {} | Ruolo: {}", 
                   sessionId, id, request.getEmail(), request.getUsername(), request.getRuolo());
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE updateUtente - Accesso admin negato", sessionId);
            return accessCheck;
        }

        // Validazione ID utente
        if (id == null || id <= 0) {
            logger.warn("[{}] FINE updateUtente - ID utente non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_USER_ID", 
                                  "ID utente non valido", 
                                  "L'ID dell'utente deve essere un numero positivo valido.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        // Validazione input
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            logger.warn("[{}] FINE updateUtente - Email mancante per utente ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_EMAIL", 
                                  "Email mancante", 
                                  "L'email è obbligatoria per l'aggiornamento dell'utente.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            logger.warn("[{}] FINE updateUtente - Username mancante per utente ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_USERNAME", 
                                  "Username mancante", 
                                  "Lo username è obbligatorio per l'aggiornamento dell'utente.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Validazioni superate, tentativo di aggiornamento utente ID: {}", sessionId, id);
        try {
            Utente updated = authService.updateUtente(id, request);
            if (updated == null) {
                logger.warn("[{}] FINE updateUtente - Utente non trovato o non modificabile - ID: {}", sessionId, id);
                return new ResponseEntity<>(
                    createErrorResponse("USER_NOT_FOUND", 
                                      "Utente non trovato o non modificabile", 
                                      String.format("L'utente con ID %d non esiste o non può essere modificato.", id), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.info("[{}] FINE updateUtente - Utente modificato con successo | ID: {} | Email: {} | Username: {}", 
                       sessionId, updated.getId(), updated.getEmail(), updated.getUsername());
            
            Map<String, Object> responseData = Map.of(
                "userId", updated.getId(),
                "email", updated.getEmail(),
                "username", updated.getUsername(),
                "nome", updated.getNome() != null ? updated.getNome() : "",
                "ruolo", updated.getRuolo() != null ? updated.getRuolo() : "USER"
            );
            
            return new ResponseEntity<>(
                createSuccessResponse("Utente aggiornato con successo dall'amministratore", responseData, sessionId),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("[{}] FINE updateUtente - Errore critico durante aggiornamento utente ID: {} | Errore: {}", 
                        sessionId, id, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante l'aggiornamento dell'utente. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Eliminazione utente
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteUtente(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO deleteUtente - ID Utente: {}", sessionId, id);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE deleteUtente - Accesso admin negato", sessionId);
            return accessCheck;
        }

        // Validazione ID utente
        if (id == null || id <= 0) {
            logger.warn("[{}] FINE deleteUtente - ID utente non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_USER_ID", 
                                  "ID utente non valido", 
                                  "L'ID dell'utente deve essere un numero positivo valido.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Accesso admin confermato, tentativo di eliminazione utente ID: {}", sessionId, id);
        try {
            boolean deleted = authService.deleteUtente(id);
            if (!deleted) {
                logger.warn("[{}] FINE deleteUtente - Utente non trovato o non eliminabile - ID: {}", sessionId, id);
                return new ResponseEntity<>(
                    createErrorResponse("USER_NOT_FOUND", 
                                      "Utente non trovato o non eliminabile", 
                                      String.format("L'utente con ID %d non esiste o non può essere eliminato.", id), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.info("[{}] FINE deleteUtente - Utente eliminato con successo - ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createSuccessResponse("Utente eliminato con successo", 
                                    Map.of("deletedUserId", id), 
                                    sessionId),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("[{}] FINE deleteUtente - Errore critico durante eliminazione utente ID: {} | Errore: {}", 
                        sessionId, id, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante l'eliminazione dell'utente. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Lista tutte le aule
    @GetMapping("/rooms")
    public ResponseEntity<?> getAllRooms(@RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getAllRooms (admin) - Richiesta lista completa aule", sessionId);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE getAllRooms - Accesso admin negato", sessionId);
            return accessCheck;
        }

        logger.info("[{}] Accesso admin confermato, recupero tutte le aule", sessionId);
        try {
            List<Aula> aule = aulaService.getAllAule();
            
            if (aule == null) {
                logger.error("[{}] FINE getAllRooms - Servizio ha restituito null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio aule", 
                                      "Si è verificato un problema nel recupero delle aule. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (aule.isEmpty()) {
                logger.info("[{}] FINE getAllRooms - Nessuna aula trovata nel sistema", sessionId);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessuna aula presente nel sistema", 
                                        Map.of("rooms", Collections.emptyList(), 
                                              "totalRooms", 0), 
                                        sessionId),
                    HttpStatus.OK
                );
            }

            logger.info("[{}] FINE getAllRooms - Aule recuperate con successo, totale: {}", sessionId, aule.size());
            return new ResponseEntity<>(
                createSuccessResponse("Lista aule recuperata con successo", 
                                    Map.of("rooms", aule, 
                                          "totalRooms", aule.size()), 
                                    sessionId),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("[{}] FINE getAllRooms - Errore critico durante recupero aule: {}", sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Ottieni singola aula per ID
    @GetMapping("/rooms/{id}")
    public ResponseEntity<?> getRoomById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getRoomById (admin) - ID Aula: {}", sessionId, id);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE getRoomById - Accesso admin negato", sessionId);
            return accessCheck;
        }

        // Validazione ID aula
        if (id == null || id <= 0) {
            logger.warn("[{}] FINE getRoomById - ID aula non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", 
                                  "ID aula non valido", 
                                  "L'ID dell'aula deve essere un numero positivo valido.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Accesso admin confermato, recupero aula con ID: {}", sessionId, id);
        try {
            java.util.Optional<Aula> aula = aulaService.getAulaById(id);
            if (aula.isEmpty()) {
                logger.warn("[{}] FINE getRoomById - Aula non trovata con ID: {}", sessionId, id);
                return new ResponseEntity<>(
                    createErrorResponse("ROOM_NOT_FOUND", 
                                      "Aula non trovata", 
                                      String.format("L'aula con ID %d non esiste.", id), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.info("[{}] FINE getRoomById - Aula recuperata con successo: ID: {}, Nome: {}", 
                       sessionId, aula.get().getId(), aula.get().getNome());
            return new ResponseEntity<>(
                createSuccessResponse("Aula recuperata con successo", 
                                    Map.of("room", aula.get()), 
                                    sessionId),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("[{}] FINE getRoomById - Errore critico per ID aula: {} | Errore: {}", sessionId, id, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero dell'aula.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Gestione stanze - Creazione stanza
    @PostMapping("/createrooms")
    public ResponseEntity<?> createRoom(@RequestBody AulaRequest roomRequest, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO createRoom | Nome: {} | Piano: {} | Capienza: {}", 
                   sessionId, roomRequest.getNome(), roomRequest.getPiano(), roomRequest.getCapienza());
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE createRoom - Accesso admin negato", sessionId);
            return accessCheck;
        }

        // Validazione input
        if (roomRequest.getNome() == null || roomRequest.getNome().trim().isEmpty()) {
            logger.warn("[{}] FINE createRoom - Nome aula mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_ROOM_NAME", 
                                  "Nome aula mancante", 
                                  "Il nome dell'aula è obbligatorio.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (roomRequest.getPiano() < 0) {
            logger.warn("[{}] FINE createRoom - Piano non valido per aula: {} | Piano: {}", 
                       sessionId, roomRequest.getNome(), roomRequest.getPiano());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_FLOOR", 
                                  "Piano non valido", 
                                  "Il piano dell'aula deve essere un numero non negativo.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (roomRequest.getCapienza() <= 0) {
            logger.warn("[{}] FINE createRoom - Capienza non valida per aula: {} | Capienza: {}", 
                       sessionId, roomRequest.getNome(), roomRequest.getCapienza());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_CAPACITY", 
                                  "Capienza non valida", 
                                  "La capienza dell'aula deve essere un numero positivo.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Validazioni superate, tentativo di creazione aula", sessionId);
        try {
            Aula nuovaAula = aulaService.createAula(roomRequest);
            if (nuovaAula == null) {
                logger.warn("[{}] FINE createRoom - Impossibile creare aula | Nome: {}", sessionId, roomRequest.getNome());
                return new ResponseEntity<>(
                    createErrorResponse("ROOM_CREATION_FAILED", 
                                      "Impossibile creare aula", 
                                      String.format("Impossibile creare l'aula '%s'. Verifica che il nome non sia già esistente.", 
                                                   roomRequest.getNome()), 
                                      sessionId),
                    HttpStatus.BAD_REQUEST
                );
            }

            logger.info("[{}] FINE createRoom - Aula creata con successo | ID: {} | Nome: {}", 
                       sessionId, nuovaAula.getId(), nuovaAula.getNome());
            
            Map<String, Object> responseData = Map.of(
                "aulaId", nuovaAula.getId(),
                "nome", nuovaAula.getNome(),
                "piano", nuovaAula.getPiano(),
                "capienza", nuovaAula.getCapienza()
            );
            
            return new ResponseEntity<>(
                createSuccessResponse("Aula creata con successo", responseData, sessionId),
                HttpStatus.CREATED
            );
        } catch (Exception e) {
            logger.error("[{}] FINE createRoom - Errore critico durante creazione aula: {}", sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante la creazione dell'aula. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Modifica stanza
    @PutMapping("/rooms/{id}")
    public ResponseEntity<?> updateRoom(@PathVariable Long id, @RequestBody AulaRequest roomRequest, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO updateRoom | ID Aula: {} | Nuovo Nome: {} | Piano: {} | Capienza: {}", 
                   sessionId, id, roomRequest.getNome(), roomRequest.getPiano(), roomRequest.getCapienza());
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE updateRoom - Accesso admin negato", sessionId);
            return accessCheck;
        }

        // Validazione ID aula
        if (id == null || id <= 0) {
            logger.warn("[{}] FINE updateRoom - ID aula non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", 
                                  "ID aula non valido", 
                                  "L'ID dell'aula deve essere un numero positivo valido.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        // Validazione input
        if (roomRequest.getNome() == null || roomRequest.getNome().trim().isEmpty()) {
            logger.warn("[{}] FINE updateRoom - Nome aula mancante per ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_ROOM_NAME", 
                                  "Nome aula mancante", 
                                  "Il nome dell'aula è obbligatorio per l'aggiornamento.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (roomRequest.getPiano() < 0) {
            logger.warn("[{}] FINE updateRoom - Piano non valido per aula ID: {} | Piano: {}", 
                       sessionId, id, roomRequest.getPiano());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_FLOOR", 
                                  "Piano non valido", 
                                  "Il piano dell'aula deve essere un numero non negativo.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (roomRequest.getCapienza() <= 0) {
            logger.warn("[{}] FINE updateRoom - Capienza non valida per aula ID: {} | Capienza: {}", 
                       sessionId, id, roomRequest.getCapienza());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_CAPACITY", 
                                  "Capienza non valida", 
                                  "La capienza dell'aula deve essere un numero positivo.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Validazioni superate, tentativo di aggiornamento aula ID: {}", sessionId, id);
        try {
            Aula aulaAggiornata = aulaService.updateAula(id, roomRequest);
            if (aulaAggiornata == null) {
                logger.warn("[{}] FINE updateRoom - Impossibile aggiornare aula ID: {}", sessionId, id);
                return new ResponseEntity<>(
                    createErrorResponse("ROOM_UPDATE_FAILED", 
                                      "Impossibile aggiornare aula", 
                                      String.format("L'aula con ID %d non esiste o non può essere aggiornata.", id), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.info("[{}] FINE updateRoom - Aula aggiornata con successo | ID: {} | Nome: {}", 
                       sessionId, aulaAggiornata.getId(), aulaAggiornata.getNome());
            
            Map<String, Object> responseData = Map.of(
                "aulaId", aulaAggiornata.getId(),
                "nome", aulaAggiornata.getNome(),
                "piano", aulaAggiornata.getPiano(),
                "capienza", aulaAggiornata.getCapienza()
            );
            
            return new ResponseEntity<>(
                createSuccessResponse("Aula aggiornata con successo", responseData, sessionId),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("[{}] FINE updateRoom - Errore critico durante aggiornamento aula ID: {} | Errore: {}", 
                        sessionId, id, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante l'aggiornamento dell'aula. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Eliminazione stanza
    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO deleteRoom - ID Aula: {}", sessionId, id);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE deleteRoom - Accesso admin negato", sessionId);
            return accessCheck;
        }

        // Validazione ID aula
        if (id == null || id <= 0) {
            logger.warn("[{}] FINE deleteRoom - ID aula non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", 
                                  "ID aula non valido", 
                                  "L'ID dell'aula deve essere un numero positivo valido.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Accesso admin confermato, tentativo di eliminazione aula ID: {}", sessionId, id);
        try {
            boolean eliminata = aulaService.deleteAula(id);
            if (!eliminata) {
                logger.warn("[{}] FINE deleteRoom - Impossibile eliminare aula ID: {}", sessionId, id);
                return new ResponseEntity<>(
                    createErrorResponse("ROOM_NOT_FOUND", 
                                      "Impossibile eliminare aula", 
                                      String.format("L'aula con ID %d non esiste o non può essere eliminata.", id), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.info("[{}] FINE deleteRoom - Aula eliminata con successo - ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createSuccessResponse("Aula eliminata con successo", 
                                    Map.of("deletedRoomId", id), 
                                    sessionId),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("[{}] FINE deleteRoom - Errore critico durante eliminazione aula ID: {} | Errore: {}", 
                        sessionId, id, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante l'eliminazione dell'aula. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // ========== GESTIONE PRENOTAZIONI ADMIN ==========

    // Elimina prenotazione come admin (può eliminare qualsiasi prenotazione)
    @DeleteMapping("/prenotazioni/{id}")
    public ResponseEntity<?> deletePrenotazioneAsAdmin(@PathVariable Long id, 
                                                      @RequestHeader("Authorization") String authHeader,
                                                      @RequestBody(required = false) Map<String, String> requestBody) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO deletePrenotazioneAsAdmin - ID Prenotazione: {}", sessionId, id);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.warn("[{}] FINE deletePrenotazioneAsAdmin - Accesso admin negato", sessionId);
            return accessCheck;
        }

        // Validazione ID prenotazione
        if (id == null || id <= 0) {
            logger.warn("[{}] FINE deletePrenotazioneAsAdmin - ID prenotazione non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_BOOKING_ID", 
                                  "ID prenotazione non valido", 
                                  "L'ID della prenotazione deve essere un numero positivo valido.", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Accesso admin confermato, tentativo di eliminazione prenotazione ID: {}", sessionId, id);
        try {
            // Estrai il token per ottenere l'ID dell'admin
            String token = authHeader.substring(7);
            Long adminId = jwtService.getUserIdFromToken(token);
            logger.info("[{}] Admin ID: {} tenta di eliminare prenotazione: {}", sessionId, adminId, id);
            
            // Motivo opzionale per l'eliminazione
            String motivo = (requestBody != null && requestBody.get("reason") != null) 
                ? requestBody.get("reason") 
                : "Eliminazione da parte dell'amministratore";
            logger.info("[{}] Motivo eliminazione: {}", sessionId, motivo);

            // Tentativo di eliminazione forzata per admin
            boolean eliminata = prenotazioneService.annullaPrenotazioneAsAdmin(id, adminId, motivo);
            
            if (!eliminata) {
                logger.warn("[{}] FINE deletePrenotazioneAsAdmin - Impossibile eliminare prenotazione ID: {}", sessionId, id);
                return new ResponseEntity<>(
                    createErrorResponse("BOOKING_NOT_FOUND", 
                                      "Impossibile eliminare prenotazione", 
                                      String.format("La prenotazione con ID %d non esiste o non può essere eliminata.", id), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.info("[{}] FINE deletePrenotazioneAsAdmin - Prenotazione eliminata con successo | ID: {} | Admin: {} | Motivo: {}", 
                       sessionId, id, adminId, motivo);
            
            Map<String, Object> responseData = Map.of(
                "deletedBookingId", id,
                "adminId", adminId,
                "adminAction", true,
                "reason", motivo
            );
            
            return new ResponseEntity<>(
                createSuccessResponse("Prenotazione eliminata con successo dall'amministratore", responseData, sessionId),
                HttpStatus.OK
            );

        } catch (Exception e) {
            logger.error("[{}] FINE deletePrenotazioneAsAdmin - Errore critico durante eliminazione prenotazione ID: {} | Errore: {}", 
                        sessionId, id, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante l'eliminazione della prenotazione. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
