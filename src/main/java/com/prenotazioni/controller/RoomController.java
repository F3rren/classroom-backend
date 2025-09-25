package com.prenotazioni.controller;

import com.prenotazioni.service.JwtService;
import com.prenotazioni.service.AulaService;
import com.prenotazioni.service.PrenotazioneService;
import com.prenotazioni.model.Aula;
import com.prenotazioni.dto.RoomDetailsResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private AulaService aulaService;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private PrenotazioneService prenotazioneService;

    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Genera un ID di sessione univoco per il tracking delle operazioni
     */
    private String generateSessionId() {
        return "R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
                    "timestamp", formatTimestamp(LocalDateTime.now()),
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
                    "timestamp", formatTimestamp(LocalDateTime.now()),
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
                    "timestamp", formatTimestamp(LocalDateTime.now()),
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
                        "timestamp", formatTimestamp(LocalDateTime.now()),
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
                    "timestamp", formatTimestamp(LocalDateTime.now()),
                    "sessionId", sessionId
                ),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        logger.info("[{}] FINE checkAuth - Token valido, accesso consentito", sessionId);
        return null; // Access granted
    }

    // Lista tutte le aule - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping
    public ResponseEntity<?> getAllRooms(@RequestHeader("Authorization") String authHeader) 
    {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getAllRooms - Richiesta lista completa aule", sessionId);

        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) 
        {
            logger.warn("[{}] FINE getAllRooms - Autenticazione fallita", sessionId);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero tutte le aule", sessionId);
        
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
                    createSuccessResponse("Nessuna aula disponibile", 
                                        Map.of("rooms", Collections.emptyList(), "totalRooms", 0), 
                                        sessionId),
                    HttpStatus.OK
                );
            }

            logger.info("[{}] FINE getAllRooms - Aule recuperate con successo, totale: {}", sessionId, aule.size());
            return new ResponseEntity<>(
                createSuccessResponse("Aule recuperate con successo", 
                                    Map.of("rooms", aule, "totalRooms", aule.size()), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getAllRooms - Errore critico durante il recupero delle aule: {}", 
                        sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule. Contatta il supporto se il problema persiste.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Vista completa di tutte le prenotazioni - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/details")
    public ResponseEntity<?> getAllRoomsWithDetails(@RequestHeader("Authorization") String authHeader) 
    {
        logger.info("INIZIO getAllRoomsWithDetails");

        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) 
        {
            logger.warn("FINE getAllRoomsWithDetails - Autenticazione fallita");
            return authCheck;
        }

        logger.info("Autenticazione riuscita, recupero dettagli completi delle prenotazioni");

        List<Map<String, Object>> dettagliCompleti;
        try 
        {
            dettagliCompleti = prenotazioneService.getAllCompleteDetails();
        } catch (Exception e) 
        {
            logger.error("Errore durante il recupero dei dettagli completi delle prenotazioni", e);
            return new ResponseEntity<>
            (
                Collections.singletonMap("error", "Errore interno durante il recupero delle prenotazioni"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (dettagliCompleti == null || dettagliCompleti.isEmpty()) 
        {
            logger.warn("FINE getAllRoomsWithDetails - Nessuna prenotazione trovata");
            return new ResponseEntity<>
            (
                Map.of
                (
                    "prenotazioni", Collections.emptyList(),
                    "totalPrenotazioni", 0
                ),
                HttpStatus.OK
            );
        }

        logger.info("FINE getAllRoomsWithDetails - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return new ResponseEntity<>
        (
            Map.of
            (
                "prenotazioni", dettagliCompleti,
                "totalPrenotazioni", dettagliCompleti.size()
            ),
            HttpStatus.OK
        );
    }

    // Ottieni singola aula per ID - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI  
    @GetMapping("/{id}")
    public ResponseEntity<?> getRoomById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) 
    {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getRoomById - ID Aula richiesta: {}", sessionId, id);

        // Validazione parametri
        if (id == null) {
            logger.warn("[{}] FINE getRoomById - ID aula mancante", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("MISSING_ROOM_ID", "ID aula mancante", 
                                  "Devi specificare l'ID dell'aula da recuperare.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (id <= 0) {
            logger.warn("[{}] FINE getRoomById - ID aula non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "ID aula non valido", 
                                  "L'ID dell'aula deve essere un numero positivo.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) 
        {
            logger.warn("[{}] FINE getRoomById - Autenticazione fallita per ID Aula: {}", sessionId, id);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero aula con ID: {}", sessionId, id);

        try {
            java.util.Optional<Aula> aula = aulaService.getAulaById(id);
            
            if (aula.isEmpty()) {
                logger.warn("[{}] FINE getRoomById - Aula non trovata con ID: {}", sessionId, id);
                return new ResponseEntity<>(
                    createErrorResponse("ROOM_NOT_FOUND", 
                                      "Aula non trovata", 
                                      String.format("L'aula con ID %d non esiste nel sistema.", id), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }

            Aula aulaFound = aula.get();
            logger.info("[{}] FINE getRoomById - Aula recuperata con successo: ID: {}, Nome: {}, Piano: {}, Capienza: {}", 
                       sessionId, aulaFound.getId(), aulaFound.getNome(), aulaFound.getPiano(), aulaFound.getCapienza());
            
            return new ResponseEntity<>(
                createSuccessResponse("Aula recuperata con successo", 
                                    Map.of("room", aulaFound,
                                          "roomId", aulaFound.getId(),
                                          "roomName", aulaFound.getNome(),
                                          "floor", aulaFound.getPiano(),
                                          "capacity", aulaFound.getCapienza()), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getRoomById - Errore critico durante il recupero dell'aula ID: {} | Errore: {}", 
                        sessionId, id, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero dell'aula. Riprova più tardi.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Ottieni dettagli completi aula con prenotazioni - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/{id}/details")
    public ResponseEntity<?> getRoomDetailsById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.info("INIZIO getRoomDetailsById - ID Aula: {}", id);

        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("FINE getRoomDetailsById - Autenticazione fallita per ID Aula: {}", id);
            return authCheck;
        }

        logger.info("Autenticazione riuscita, recupero dettagli completi per aula con ID: {}", id);

        java.util.Optional<Aula> aula;
        try {
            aula = aulaService.getAulaById(id);
        } catch (Exception e) {
            logger.error("Errore durante il recupero dell'aula con ID: {}", id, e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno durante il recupero dell'aula"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (aula.isEmpty()) {
            logger.warn("FINE getRoomDetailsById - Aula non trovata con ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Aula non trovata"),
                HttpStatus.NOT_FOUND
            );
        }

        logger.info("Aula trovata: ID: {}, Nome: {}", aula.get().getId(), aula.get().getNome());

        List<Map<String, Object>> dettagliCompleti;
        try {
            dettagliCompleti = prenotazioneService.getRoomCompleteDetails(id);
        } catch (Exception e) {
            logger.error("Errore durante il recupero dei dettagli completi per l'aula con ID: {}", id, e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno durante il recupero dei dettagli delle prenotazioni"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        if (dettagliCompleti == null || dettagliCompleti.isEmpty()) {
            logger.warn("FINE getRoomDetailsById - Nessuna prenotazione trovata per l'aula con ID: {}", id);
            return new ResponseEntity<>(
                Map.of(
                    "aula", aula.get(),
                    "prenotazioni", Collections.emptyList(),
                    "totalPrenotazioni", 0
                ),
                HttpStatus.OK
            );
        }

        logger.info("FINE getRoomDetailsById - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return new ResponseEntity<>(
            Map.of(
                "aula", aula.get(),
                "prenotazioni", dettagliCompleti,
                "totalPrenotazioni", dettagliCompleti.size()
            ),
            HttpStatus.OK
        );
    }

    // Filtra aule per piano - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/piano/{piano}")
    public ResponseEntity<?> getRoomsByFloor(@PathVariable int piano, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getRoomsByFloor - Piano richiesto: {}", sessionId, piano);
        
        // Validazione parametri
        if (piano < 0) {
            logger.warn("[{}] FINE getRoomsByFloor - Piano non valido: {}", sessionId, piano);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_FLOOR", "Piano non valido", 
                                  "Il numero del piano deve essere maggiore o uguale a 0.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getRoomsByFloor - Autenticazione fallita per piano: {}", sessionId, piano);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero aule per piano: {}", sessionId, piano);
        
        try {
            List<Aula> aule = aulaService.getAuleByPiano(piano);
            
            if (aule == null) {
                logger.error("[{}] FINE getRoomsByFloor - Servizio ha restituito null per piano: {}", sessionId, piano);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio aule", 
                                      "Si è verificato un problema nel recupero delle aule per piano. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (aule.isEmpty()) {
                logger.info("[{}] FINE getRoomsByFloor - Nessuna aula trovata per il piano: {}", sessionId, piano);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessuna aula trovata per questo piano", 
                                        Map.of("piano", piano, 
                                              "rooms", Collections.emptyList(), 
                                              "totalRooms", 0), 
                                        sessionId),
                    HttpStatus.OK
                );
            }
            
            logger.info("[{}] FINE getRoomsByFloor - Aule recuperate con successo per piano: {}, totale: {}", 
                       sessionId, piano, aule.size());
            return new ResponseEntity<>(
                createSuccessResponse("Aule recuperate con successo", 
                                    Map.of("piano", piano,
                                          "rooms", aule, 
                                          "totalRooms", aule.size()), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getRoomsByFloor - Errore critico durante il recupero aule piano: {} | Errore: {}", 
                        sessionId, piano, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule per piano.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Filtra aule per capienza minima - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/capienza")
    public ResponseEntity<?> getRoomsByCapacity(@RequestParam int minCapienza, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getRoomsByCapacity - Capienza minima richiesta: {}", sessionId, minCapienza);
        
        // Validazione parametri
        if (minCapienza < 0) {
            logger.warn("[{}] FINE getRoomsByCapacity - Capienza minima non valida: {}", sessionId, minCapienza);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_CAPACITY", "Capienza non valida", 
                                  "La capienza minima deve essere un numero maggiore o uguale a 0.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        if (minCapienza > 1000) { // Limite ragionevole
            logger.warn("[{}] FINE getRoomsByCapacity - Capienza minima troppo alta: {}", sessionId, minCapienza);
            return new ResponseEntity<>(
                createErrorResponse("CAPACITY_TOO_HIGH", "Capienza troppo elevata", 
                                  "La capienza minima richiesta è troppo alta. Inserisci un valore realistico (massimo 1000).", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getRoomsByCapacity - Autenticazione fallita per capienza: {}", sessionId, minCapienza);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero aule con capienza minima di: {}", sessionId, minCapienza);
        
        try {
            List<Aula> aule = aulaService.getAuleByCapienzaMinima(minCapienza);
            
            if (aule == null) {
                logger.error("[{}] FINE getRoomsByCapacity - Servizio ha restituito null per capienza: {}", sessionId, minCapienza);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio aule", 
                                      "Si è verificato un problema nel recupero delle aule per capienza. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (aule.isEmpty()) {
                logger.info("[{}] FINE getRoomsByCapacity - Nessuna aula trovata con capienza >= {}", sessionId, minCapienza);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessuna aula trovata con la capienza richiesta", 
                                        Map.of("capienzaMinima", minCapienza,
                                              "rooms", Collections.emptyList(), 
                                              "totalRooms", 0,
                                              "suggestion", "Prova con una capienza minore"), 
                                        sessionId),
                    HttpStatus.OK
                );
            }
            
            logger.info("[{}] FINE getRoomsByCapacity - Aule recuperate con successo per capienza >= {}, totale: {}", 
                       sessionId, minCapienza, aule.size());
            return new ResponseEntity<>(
                createSuccessResponse("Aule recuperate con successo", 
                                    Map.of("capienzaMinima", minCapienza,
                                          "rooms", aule, 
                                          "totalRooms", aule.size(),
                                          "maxCapacityFound", aule.stream().mapToInt(Aula::getCapienza).max().orElse(0)), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getRoomsByCapacity - Errore critico durante il recupero aule per capienza: {} | Errore: {}", 
                        sessionId, minCapienza, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule per capienza.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Endpoint per ottenere tutte le aule con dettagli completi (formato mock-like)
    @GetMapping("/detailed")
    public ResponseEntity<?> getAllRoomsDetailed(@RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getAllRoomsDetailed - Richiesta aule con dettagli completi", sessionId);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getAllRoomsDetailed - Autenticazione fallita", sessionId);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero tutte le aule con dettagli completi", sessionId);
        
        try {
            List<RoomDetailsResponse> roomDetails = aulaService.getAllRoomsWithDetails();
            
            if (roomDetails == null) {
                logger.error("[{}] FINE getAllRoomsDetailed - Servizio ha restituito null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio aule", 
                                      "Si è verificato un problema nel recupero dei dettagli delle aule. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (roomDetails.isEmpty()) {
                logger.info("[{}] FINE getAllRoomsDetailed - Nessuna aula con dettagli trovata", sessionId);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessuna aula con dettagli disponibile", 
                                        Map.of("rooms", Collections.emptyList(), 
                                              "totalRooms", 0), 
                                        sessionId),
                    HttpStatus.OK
                );
            }

            logger.info("[{}] FINE getAllRoomsDetailed - Aule con dettagli recuperate con successo, totale: {}", 
                       sessionId, roomDetails.size());
            return new ResponseEntity<>(
                createSuccessResponse("Aule con dettagli recuperate con successo", 
                                    Map.of("rooms", roomDetails, 
                                          "totalRooms", roomDetails.size()), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getAllRoomsDetailed - Errore critico: {}", sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule con dettagli.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Endpoint per ottenere una singola aula con dettagli completi
    @GetMapping("/{id}/detailed")
    public ResponseEntity<?> getRoomDetailed(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getRoomDetailed - ID Aula: {}", sessionId, id);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getRoomDetailed - Autenticazione fallita per aula ID: {}", sessionId, id);
            return authCheck;
        }

        // Validazione input
        if (id == null || id <= 0) {
            logger.warn("[{}] FINE getRoomDetailed - ID aula non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", 
                                  "ID aula non valido", 
                                  "L'ID dell'aula deve essere un numero positivo maggiore di 0", 
                                  sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.info("[{}] Autenticazione riuscita, recupero dettagli per aula ID: {}", sessionId, id);
        
        try {
            RoomDetailsResponse roomDetails = aulaService.getRoomWithDetails(id);
            
            if (roomDetails == null) {
                logger.warn("[{}] FINE getRoomDetailed - Aula con ID {} non trovata", sessionId, id);
                return new ResponseEntity<>(
                    createErrorResponse("ROOM_NOT_FOUND", 
                                      "Aula non trovata", 
                                      String.format("Non è stata trovata nessuna aula con ID %d", id), 
                                      sessionId),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.info("[{}] FINE getRoomDetailed - Dettagli aula recuperati con successo: ID: {}, Nome: {}", 
                       sessionId, roomDetails.getId(), roomDetails.getName());
            return new ResponseEntity<>(
                createSuccessResponse("Dettagli aula recuperati con successo", 
                                    Map.of("room", roomDetails), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getRoomDetailed - Errore critico recuperando dettagli aula ID {}: {}", 
                        sessionId, id, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  String.format("Si è verificato un errore durante il recupero dei dettagli dell'aula con ID %d", id), 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Endpoint per ottenere solo le aule fisiche
    @GetMapping("/physical")
    public ResponseEntity<?> getPhysicalRooms(@RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getPhysicalRooms - Richiesta aule fisiche", sessionId);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getPhysicalRooms - Autenticazione fallita", sessionId);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero aule fisiche", sessionId);
        
        try {
            List<Aula> aule = aulaService.getPhysicalRoomsOrdered();
            
            if (aule == null) {
                logger.error("[{}] FINE getPhysicalRooms - Servizio ha restituito null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio aule", 
                                      "Si è verificato un problema nel recupero delle aule fisiche. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (aule.isEmpty()) {
                logger.info("[{}] FINE getPhysicalRooms - Nessuna aula fisica trovata", sessionId);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessuna aula fisica disponibile", 
                                        Map.of("rooms", Collections.emptyList(),
                                              "totalRooms", 0, 
                                              "type", "physical"), 
                                        sessionId),
                    HttpStatus.OK
                );
            }

            logger.info("[{}] FINE getPhysicalRooms - Aule fisiche recuperate con successo, totale: {}", 
                       sessionId, aule.size());
            return new ResponseEntity<>(
                createSuccessResponse("Aule fisiche recuperate con successo", 
                                    Map.of("rooms", aule,
                                          "totalRooms", aule.size(), 
                                          "type", "physical"), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getPhysicalRooms - Errore critico durante il recupero aule fisiche: {}", 
                        sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule fisiche.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Endpoint per ottenere solo le aule virtuali
    @GetMapping("/virtual")
    public ResponseEntity<?> getVirtualRooms(@RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getVirtualRooms - Richiesta aule virtuali", sessionId);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getVirtualRooms - Autenticazione fallita", sessionId);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero aule virtuali", sessionId);
        
        try {
            List<Aula> aule = aulaService.getVirtualRoomsOrdered();
            
            if (aule == null) {
                logger.error("[{}] FINE getVirtualRooms - Servizio ha restituito null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio aule", 
                                      "Si è verificato un problema nel recupero delle aule virtuali. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (aule.isEmpty()) {
                logger.info("[{}] FINE getVirtualRooms - Nessuna aula virtuale trovata", sessionId);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessuna aula virtuale disponibile", 
                                        Map.of("rooms", Collections.emptyList(),
                                              "totalRooms", 0, 
                                              "type", "virtual"), 
                                        sessionId),
                    HttpStatus.OK
                );
            }

            logger.info("[{}] FINE getVirtualRooms - Aule virtuali recuperate con successo, totale: {}", 
                       sessionId, aule.size());
            return new ResponseEntity<>(
                createSuccessResponse("Aule virtuali recuperate con successo", 
                                    Map.of("rooms", aule,
                                          "totalRooms", aule.size(), 
                                          "type", "virtual"), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getVirtualRooms - Errore critico durante il recupero aule virtuali: {}", 
                        sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule virtuali.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Endpoint per ottenere aule fisiche con dettagli completi
    @GetMapping("/physical/detailed")
    public ResponseEntity<?> getPhysicalRoomsDetailed(@RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getPhysicalRoomsDetailed - Richiesta aule fisiche con dettagli", sessionId);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getPhysicalRoomsDetailed - Autenticazione fallita", sessionId);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero aule fisiche con dettagli completi", sessionId);
        
        try {
            List<RoomDetailsResponse> roomDetails = aulaService.getPhysicalRoomsWithDetails();
            
            if (roomDetails == null) {
                logger.error("[{}] FINE getPhysicalRoomsDetailed - Servizio ha restituito null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio aule", 
                                      "Si è verificato un problema nel recupero delle aule fisiche con dettagli. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (roomDetails.isEmpty()) {
                logger.info("[{}] FINE getPhysicalRoomsDetailed - Nessuna aula fisica con dettagli trovata", sessionId);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessuna aula fisica con dettagli disponibile", 
                                        Map.of("rooms", Collections.emptyList(),
                                              "totalRooms", 0, 
                                              "type", "physical"), 
                                        sessionId),
                    HttpStatus.OK
                );
            }

            logger.info("[{}] FINE getPhysicalRoomsDetailed - Aule fisiche con dettagli recuperate con successo, totale: {}", 
                       sessionId, roomDetails.size());
            return new ResponseEntity<>(
                createSuccessResponse("Aule fisiche con dettagli recuperate con successo", 
                                    Map.of("rooms", roomDetails,
                                          "totalRooms", roomDetails.size(), 
                                          "type", "physical"), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getPhysicalRoomsDetailed - Errore critico durante il recupero aule fisiche con dettagli: {}", 
                        sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule fisiche con dettagli.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Endpoint per ottenere aule virtuali con dettagli completi
    @GetMapping("/virtual/detailed")
    public ResponseEntity<?> getVirtualRoomsDetailed(@RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getVirtualRoomsDetailed - Richiesta aule virtuali con dettagli", sessionId);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getVirtualRoomsDetailed - Autenticazione fallita", sessionId);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, recupero aule virtuali con dettagli completi", sessionId);
        
        try {
            List<RoomDetailsResponse> roomDetails = aulaService.getVirtualRoomsWithDetails();
            
            if (roomDetails == null) {
                logger.error("[{}] FINE getVirtualRoomsDetailed - Servizio ha restituito null", sessionId);
                return new ResponseEntity<>(
                    createErrorResponse("SERVICE_ERROR", 
                                      "Errore nel servizio aule", 
                                      "Si è verificato un problema nel recupero delle aule virtuali con dettagli. Riprova più tardi.", 
                                      sessionId),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            
            if (roomDetails.isEmpty()) {
                logger.info("[{}] FINE getVirtualRoomsDetailed - Nessuna aula virtuale con dettagli trovata", sessionId);
                return new ResponseEntity<>(
                    createSuccessResponse("Nessuna aula virtuale con dettagli disponibile", 
                                        Map.of("rooms", Collections.emptyList(),
                                              "totalRooms", 0, 
                                              "type", "virtual"), 
                                        sessionId),
                    HttpStatus.OK
                );
            }

            logger.info("[{}] FINE getVirtualRoomsDetailed - Aule virtuali con dettagli recuperate con successo, totale: {}", 
                       sessionId, roomDetails.size());
            return new ResponseEntity<>(
                createSuccessResponse("Aule virtuali con dettagli recuperate con successo", 
                                    Map.of("rooms", roomDetails,
                                          "totalRooms", roomDetails.size(), 
                                          "type", "virtual"), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getVirtualRoomsDetailed - Errore critico durante il recupero aule virtuali con dettagli: {}", 
                        sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il recupero delle aule virtuali con dettagli.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Endpoint per ottenere statistiche aule fisiche vs virtuali
    @GetMapping("/stats")
    public ResponseEntity<?> getRoomsStats(@RequestHeader("Authorization") String authHeader) {
        String sessionId = generateSessionId();
        logger.info("[{}] INIZIO getRoomsStats - Richiesta statistiche aule", sessionId);

        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.warn("[{}] FINE getRoomsStats - Autenticazione fallita", sessionId);
            return authCheck;
        }

        logger.info("[{}] Autenticazione riuscita, calcolo statistiche aule", sessionId);

        try {
            long physicalCount = aulaService.countPhysicalRooms();
            long virtualCount = aulaService.countVirtualRooms();
            long totalCount = physicalCount + virtualCount;
            
            // Calcoli percentuali per evitare divisione per zero
            double physicalPercentage = totalCount > 0 ? (double) physicalCount / totalCount * 100 : 0.0;
            double virtualPercentage = totalCount > 0 ? (double) virtualCount / totalCount * 100 : 0.0;

            Map<String, Object> stats = Map.of(
                "totalRooms", totalCount,
                "physicalRooms", physicalCount,
                "virtualRooms", virtualCount,
                "physicalPercentage", Math.round(physicalPercentage * 100.0) / 100.0,
                "virtualPercentage", Math.round(virtualPercentage * 100.0) / 100.0,
                "hasRooms", totalCount > 0
            );

            logger.info("[{}] FINE getRoomsStats - Statistiche calcolate: Totale: {}, Fisiche: {}, Virtuali: {}", 
                       sessionId, totalCount, physicalCount, virtualCount);
                       
            return new ResponseEntity<>(
                createSuccessResponse("Statistiche aule recuperate con successo", 
                                    Map.of("statistics", stats), 
                                    sessionId),
                HttpStatus.OK
            );
            
        } catch (Exception e) {
            logger.error("[{}] FINE getRoomsStats - Errore critico durante il calcolo statistiche: {}", 
                        sessionId, e.getMessage(), e);
            return new ResponseEntity<>(
                createErrorResponse("INTERNAL_ERROR", 
                                  "Errore interno del server", 
                                  "Si è verificato un errore durante il calcolo delle statistiche aule.", 
                                  sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
