package com.prenotazioni.auth.controller;

import com.prenotazioni.auth.dto.CreateUserRequest;
import com.prenotazioni.auth.dto.DeletedUserResponse;
import com.prenotazioni.auth.dto.UpdateUserRequest;
import com.prenotazioni.auth.dto.UserListPayload;
import com.prenotazioni.auth.dto.UserRegisterAck;
import com.prenotazioni.auth.dto.UserSummaryDto;
import com.prenotazioni.auth.dto.UserUpdateAck;
import com.prenotazioni.auth.model.Utente;
import com.prenotazioni.auth.service.AuthService;
import com.prenotazioni.auth.service.UtenteService;
import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.util.LogSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Amministrazione degli utenti.
 *
 * Questi endpoint stavano in AdminController insieme a quelli su aule e prenotazioni: un
 * solo controller che iniettava cinque servizi di tre domini diversi. Il percorso resta
 * /api/admin/... perche' e' cio' che il frontend chiama, ma ora e' il gateway a decidere
 * quale servizio lo serve.
 *
 * hasRole('ADMIN') funziona qui esattamente come prima: il ruolo arriva dal token, quindi
 * non serve interrogare nulla per autorizzare.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Amministrazione utenti")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUtentiController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUtentiController.class);

    private final AuthService authService;
    private final UtenteService utenteService;

    AdminUtentiController(AuthService authService, UtenteService utenteService) {
        this.authService = authService;
        this.utenteService = utenteService;
    }

    private String generateSessionId() {
        return "ADM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private <T> ApiEnvelope<T> createErrorResponse(String errorCode, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(errorCode, message, userMessage, sessionId);
    }

    private <T> ApiEnvelope<T> createSuccessResponse(String message, T data, String sessionId) {
        return ApiEnvelope.success(message, data, sessionId);
    }

    @PostMapping("/register")
    @Operation(summary = "Crea un nuovo utente (solo admin)")
    public ResponseEntity<ApiEnvelope<UserRegisterAck>> register(@Valid @RequestBody CreateUserRequest request) {
        String sessionId = generateSessionId();
        logger.debug("[{}] register - creazione utente da admin | {} | ruolo={}",
                   sessionId, LogSanitizer.maskEmail(request.getEmail()), request.getRuolo());

        Utente utente = authService.register(request);

        if (utente == null) {
            logger.warn("[{}] register - rifiutata, email o username già esistenti ({})",
                       sessionId, LogSanitizer.maskEmail(request.getEmail()));
            return new ResponseEntity<>(
                createErrorResponse("USER_ALREADY_EXISTS",
                                  "Email o username già esistenti",
                                  String.format("Un utente con email %s o username %s esiste già. Usa credenziali diverse.",
                                               request.getEmail(), request.getUsername()),
                                  sessionId),
                HttpStatus.CONFLICT
            );
        }

        logger.info("[{}] Utente creato da admin - utenteId={} ruolo={}",
                   sessionId, utente.getId(), utente.getRuolo());

        return new ResponseEntity<>(
            createSuccessResponse("Utente registrato con successo dall'amministratore", new UserRegisterAck(utente), sessionId),
            HttpStatus.CREATED
        );
    }

    @GetMapping("/users")
    @Operation(summary = "Elenca tutti gli utenti (solo admin)")
    public ResponseEntity<ApiEnvelope<UserListPayload>> getAllUsers() {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO getAllUsers - Richiesta lista completa utenti", sessionId);

        List<Utente> users = authService.getAllUsers();
        List<UserSummaryDto> safeUsers = users.stream()
            .map(UserSummaryDto::forAdminListing)
            .collect(Collectors.toList());

        logger.debug("[{}] FINE getAllUsers - Utenti recuperati con successo, totale: {}", sessionId, users.size());
        return new ResponseEntity<>(
            createSuccessResponse("Lista utenti recuperata con successo", new UserListPayload(safeUsers), sessionId),
            HttpStatus.OK
        );
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Modifica un utente esistente (solo admin)")
    public ResponseEntity<ApiEnvelope<UserUpdateAck>> updateUtente(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        String sessionId = generateSessionId();
        logger.debug("[{}] updateUtente - utenteId={} ruolo={}", sessionId, id, request.getRuolo());

        if (id == null || id <= 0) {
            logger.warn("[{}] FINE updateUtente - ID utente non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_USER_ID", "ID utente non valido",
                                  "L'ID dell'utente deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Utente updated = authService.updateUtente(id, request);
        if (updated == null) {
            logger.warn("[{}] FINE updateUtente - Utente non trovato o non modificabile - ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("USER_NOT_FOUND", "Utente non trovato o non modificabile",
                                  String.format("L'utente con ID %d non esiste o non può essere modificato.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.info("[{}] Utente modificato da admin - utenteId={}", sessionId, updated.getId());

        return new ResponseEntity<>(
            createSuccessResponse("Utente aggiornato con successo dall'amministratore", new UserUpdateAck(updated), sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "Elimina un utente e i suoi dati (solo admin)")
    public ResponseEntity<ApiEnvelope<DeletedUserResponse>> deleteUtente(@PathVariable Long id) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO deleteUtente - ID Utente: {}", sessionId, id);

        if (id == null || id <= 0) {
            logger.warn("[{}] FINE deleteUtente - ID utente non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_USER_ID", "ID utente non valido",
                                  "L'ID dell'utente deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (utenteService.findById(id) == null) {
            logger.warn("[{}] FINE deleteUtente - Utente non trovato - ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("USER_NOT_FOUND", "Utente non trovato o non eliminabile",
                                  String.format("L'utente con ID %d non esiste o non può essere eliminato.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        // Elimina in cascata notifiche e prenotazioni dell'utente prima dell'utente stesso
        // (in un'unica transazione, cosi' non si rischia di lasciare righe orfane o un FK violation non gestito)
        utenteService.deleteById(id);

        logger.debug("[{}] FINE deleteUtente - Utente eliminato con successo - ID: {}", sessionId, id);
        return new ResponseEntity<>(
            createSuccessResponse("Utente eliminato con successo", new DeletedUserResponse(id), sessionId),
            HttpStatus.OK
        );
    }

    // ==================== ROOM MANAGEMENT ENDPOINTS ====================
}
