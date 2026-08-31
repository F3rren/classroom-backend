package com.prenotazioni.controller;

import com.prenotazioni.dto.*;
import com.prenotazioni.exception.BookingConflictException;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.Utente;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.service.PrenotazioneService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/prenotazioni")
@Tag(name = "Prenotazioni")
public class PrenotazioneController {

    private static final Logger logger = LoggerFactory.getLogger(PrenotazioneController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrenotazioneService prenotazioneService;

    PrenotazioneController(PrenotazioneService prenotazioneService) {
        this.prenotazioneService = prenotazioneService;
    }

    private String generateSessionId() {
        return "S" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String formatTimestamp(LocalDateTime dateTime) {
        return dateTime.format(TIMESTAMP_FORMATTER);
    }

    private <T> ApiEnvelope<T> createErrorResponse(String error, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(error, message, userMessage, sessionId);
    }

    private <T> ApiEnvelope<T> createSuccessResponse(String message, T data, String sessionId) {
        return ApiEnvelope.success(message, data, sessionId);
    }

    // Rimuove i dati personali del proprietario dagli elenchi visibili a tutti gli utenti autenticati
    // (mantiene solo id/username/nome, mai email/ruolo/date di accesso di un utente diverso dal chiamante)
    private Prenotazione sanitizeOwnerForListing(Prenotazione p) {
        Utente owner = p.getUtente();
        if (owner != null) {
            Utente safeOwner = new Utente();
            safeOwner.setId(owner.getId());
            safeOwner.setUsername(owner.getUsername());
            safeOwner.setNome(owner.getNome());
            p.setUtente(safeOwner);
        }
        return p;
    }

    // Prenota un'aula
    @PostMapping("/prenota")
    @Operation(summary = "Prenota un'aula")
    public ResponseEntity<ApiEnvelope<BookingAckPayload>> prenotaAula(@Valid @RequestBody PrenotazioneRequest request,
                                        @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO prenotaAula - AulaId: {}, CorsoId: {}, Periodo: {} - {}",
                   sessionId, request.getAulaId(), request.getCorsoId(), request.getInizio(), request.getFine());

        LocalDateTime inizio;
        LocalDateTime fine;
        try {
            inizio = LocalDateTime.parse(request.getInizio());
        } catch (DateTimeParseException e) {
            logger.warn("[{}] FINE prenotaAula - Errore parsing data inizio: '{}'", sessionId, request.getInizio());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Formato data di inizio non valido",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            fine = LocalDateTime.parse(request.getFine());
        } catch (DateTimeParseException e) {
            logger.warn("[{}] FINE prenotaAula - Errore parsing data fine: '{}'", sessionId, request.getFine());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Formato data di fine non valido",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (fine.isBefore(inizio)) {
            logger.warn("[{}] FINE prenotaAula - Data fine precedente alla data inizio", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Range temporale non valido",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (inizio.isBefore(LocalDateTime.now())) {
            logger.warn("[{}] FINE prenotaAula - Tentativo di prenotazione nel passato: {}", sessionId, formatTimestamp(inizio));
            return new ResponseEntity<>(
                createErrorResponse("PAST_DATE", "Data nel passato",
                                  "Non puoi prenotare un'aula per una data già trascorsa.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("[{}] Validazioni superate, tentativo prenotazione per periodo: {} - {}", sessionId, formatTimestamp(inizio), formatTimestamp(fine));

        Prenotazione prenotazione;
        try {
            prenotazione = prenotazioneService.prenotaAula(
                request.getAulaId(), request.getCorsoId(), principal.id(), inizio, fine, request.getDescrizione());
        } catch (DataIntegrityViolationException e) {
            logger.warn("[{}] FINE prenotaAula - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - AulaId: {}",
                       sessionId, request.getAulaId());
            throw new BookingConflictException("BOOKING_CONFLICT", "Impossibile prenotare l'aula",
                    "L'aula è appena stata prenotata da un'altra richiesta per lo stesso periodo. Riprova con un altro orario.");
        }

        if (prenotazione == null) {
            logger.warn("[{}] FINE prenotaAula - Prenotazione rifiutata dal servizio - AulaId: {}, Periodo: {} - {}",
                       sessionId, request.getAulaId(), formatTimestamp(inizio), formatTimestamp(fine));
            return new ResponseEntity<>(
                createErrorResponse("BOOKING_CONFLICT", "Impossibile prenotare l'aula",
                                  "L'aula non è disponibile nel periodo richiesto. Potrebbe essere già prenotata o fuori servizio.", sessionId),
                HttpStatus.CONFLICT
            );
        }

        logger.debug("[{}] FINE prenotaAula - Prenotazione creata con successo - ID: {}, AulaId: {}, UtenteId: {}",
                   sessionId, prenotazione.getId(), request.getAulaId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione effettuata con successo",
                                new BookingAckPayload(prenotazione, request.getAulaId(), formatTimestamp(inizio) + " - " + formatTimestamp(fine)),
                                sessionId),
            HttpStatus.CREATED
        );
    }

    // Modifica una prenotazione esistente
    @PutMapping("/{prenotazioneId}")
    @Operation(summary = "Modifica una prenotazione esistente (solo proprietario o admin)")
    public ResponseEntity<ApiEnvelope<BookingAckPayload>> modificaPrenotazione(@PathVariable Long prenotazioneId,
                                                 @Valid @RequestBody PrenotazioneRequest request,
                                                 @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO modificaPrenotazione - PrenotazioneId: {}, AulaId: {}, CorsoId: {}, Periodo: {} - {}",
                   sessionId, prenotazioneId, request.getAulaId(), request.getCorsoId(), request.getInizio(), request.getFine());

        LocalDateTime inizio;
        LocalDateTime fine;
        try {
            inizio = LocalDateTime.parse(request.getInizio());
        } catch (DateTimeParseException e) {
            logger.warn("[{}] FINE modificaPrenotazione - Errore parsing data inizio: '{}'", sessionId, request.getInizio());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Formato data di inizio non valido",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            fine = LocalDateTime.parse(request.getFine());
        } catch (DateTimeParseException e) {
            logger.warn("[{}] FINE modificaPrenotazione - Errore parsing data fine: '{}'", sessionId, request.getFine());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Formato data di fine non valido",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (fine.isBefore(inizio)) {
            logger.warn("[{}] FINE modificaPrenotazione - Data fine precedente alla data inizio", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Range temporale non valido",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (inizio.isBefore(LocalDateTime.now())) {
            logger.warn("[{}] FINE modificaPrenotazione - Tentativo di modifica con data nel passato: {}", sessionId, formatTimestamp(inizio));
            return new ResponseEntity<>(
                createErrorResponse("PAST_DATE", "Data nel passato",
                                  "Non puoi modificare una prenotazione per una data già trascorsa.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("[{}] Validazioni superate, tentativo modifica prenotazione ID {} per periodo: {} - {}",
                    sessionId, prenotazioneId, formatTimestamp(inizio), formatTimestamp(fine));

        Prenotazione prenotazione;
        try {
            prenotazione = prenotazioneService.updatePrenotazione(
                prenotazioneId, request.getAulaId(), request.getCorsoId(), principal.id(), inizio, fine, request.getDescrizione());
        } catch (DataIntegrityViolationException e) {
            logger.warn("[{}] FINE modificaPrenotazione - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - PrenotazioneId: {}, AulaId: {}",
                       sessionId, prenotazioneId, request.getAulaId());
            throw new BookingConflictException("UPDATE_CONFLICT", "Impossibile modificare la prenotazione",
                    "L'aula è appena stata prenotata da un'altra richiesta per il nuovo periodo. Riprova con un altro orario.");
        }

        if (prenotazione == null) {
            logger.warn("[{}] FINE modificaPrenotazione - Modifica rifiutata dal servizio - PrenotazioneId: {}, AulaId: {}, Periodo: {} - {}",
                       sessionId, prenotazioneId, request.getAulaId(), formatTimestamp(inizio), formatTimestamp(fine));
            return new ResponseEntity<>(
                createErrorResponse("UPDATE_FAILED", "Impossibile modificare la prenotazione",
                                  "La prenotazione non può essere modificata. Potrebbe non esistere, non avere i permessi o l'aula non è disponibile nel nuovo periodo.", sessionId),
                HttpStatus.CONFLICT
            );
        }

        logger.debug("[{}] FINE modificaPrenotazione - Prenotazione modificata con successo - ID: {}, AulaId: {}, UtenteId: {}",
                   sessionId, prenotazione.getId(), request.getAulaId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione modificata con successo",
                                new BookingAckPayload(prenotazione, request.getAulaId(), formatTimestamp(inizio) + " - " + formatTimestamp(fine)),
                                sessionId),
            HttpStatus.OK
        );
    }

    // Blocca un'aula (solo admin)
    @PostMapping("/blocca")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Blocca un'aula per un periodo (solo admin)")
    public ResponseEntity<ApiEnvelope<BlockAckPayload>> bloccaAula(@Valid @RequestBody PrenotazioneRequest request,
                                       @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO bloccaAula - AulaId: {}, Periodo: {} - {}",
                   sessionId, request.getAulaId(), request.getInizio(), request.getFine());

        LocalDateTime inizio;
        LocalDateTime fine;
        try {
            inizio = LocalDateTime.parse(request.getInizio());
        } catch (DateTimeParseException e) {
            logger.warn("[{}] FINE bloccaAula - Errore parsing data inizio: '{}'", sessionId, request.getInizio());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Formato data di inizio non valido",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            fine = LocalDateTime.parse(request.getFine());
        } catch (DateTimeParseException e) {
            logger.warn("[{}] FINE bloccaAula - Errore parsing data fine: '{}'", sessionId, request.getFine());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Formato data di fine non valido",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (fine.isBefore(inizio)) {
            logger.warn("[{}] FINE bloccaAula - Data fine precedente alla data inizio", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Range temporale non valido",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("[{}] Validazioni superate, tentativo blocco aula per periodo: {} - {}", sessionId, formatTimestamp(inizio), formatTimestamp(fine));

        Prenotazione blocco;
        try {
            blocco = prenotazioneService.bloccaAula(request.getAulaId(), principal.id(), inizio, fine, request.getDescrizione());
        } catch (DataIntegrityViolationException e) {
            logger.warn("[{}] FINE bloccaAula - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - AulaId: {}",
                       sessionId, request.getAulaId());
            throw new BookingConflictException("BLOCK_CONFLICT", "Impossibile bloccare l'aula",
                    "L'aula è appena stata occupata da un'altra richiesta per lo stesso periodo.");
        }

        if (blocco == null) {
            logger.warn("[{}] FINE bloccaAula - Blocco rifiutato dal servizio - AulaId: {}, Periodo: {} - {}",
                       sessionId, request.getAulaId(), formatTimestamp(inizio), formatTimestamp(fine));
            return new ResponseEntity<>(
                createErrorResponse("BLOCK_CONFLICT", "Impossibile bloccare l'aula",
                                  "L'aula non può essere bloccata nel periodo richiesto. Potrebbe essere già occupata.", sessionId),
                HttpStatus.CONFLICT
            );
        }

        logger.debug("[{}] FINE bloccaAula - Aula bloccata con successo - ID blocco: {}, AulaId: {}, Admin: {}",
                   sessionId, blocco.getId(), request.getAulaId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Aula bloccata con successo",
                                new BlockAckPayload(blocco, request.getAulaId(), formatTimestamp(inizio) + " - " + formatTimestamp(fine), principal.id()),
                                sessionId),
            HttpStatus.CREATED
        );
    }

    // Verifica disponibilità aula
    @GetMapping("/disponibilita")
    @Operation(summary = "Verifica la disponibilità di un'aula in un periodo")
    public ResponseEntity<ApiEnvelope<AvailabilityPayload>> verificaDisponibilita(@RequestParam Long aulaId,
                                                   @RequestParam String inizio,
                                                   @RequestParam String fine) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO verificaDisponibilita - AulaId: {}, Periodo: {} - {}", sessionId, aulaId, inizio, fine);

        LocalDateTime inizioDateTime;
        LocalDateTime fineDateTime;
        try {
            inizioDateTime = LocalDateTime.parse(inizio);
        } catch (DateTimeParseException e) {
            logger.warn("[{}] FINE verificaDisponibilita - Errore parsing data inizio: '{}'", sessionId, inizio);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Formato data di inizio non valido",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            fineDateTime = LocalDateTime.parse(fine);
        } catch (DateTimeParseException e) {
            logger.warn("[{}] FINE verificaDisponibilita - Errore parsing data fine: '{}'", sessionId, fine);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Formato data di fine non valido",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (fineDateTime.isBefore(inizioDateTime)) {
            logger.warn("[{}] FINE verificaDisponibilita - Data fine precedente alla data inizio", sessionId);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Range temporale non valido",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("[{}] Verifica disponibilità per AulaId: {} nel periodo: {} - {}", sessionId, aulaId, formatTimestamp(inizioDateTime), formatTimestamp(fineDateTime));
        boolean disponibile = prenotazioneService.isAulaDisponibile(aulaId, inizioDateTime, fineDateTime);
        logger.debug("[{}] FINE verificaDisponibilita - AulaId: {}, Disponibile: {}", sessionId, aulaId, disponibile);

        return new ResponseEntity<>(
            createSuccessResponse("Verifica disponibilità completata",
                                new AvailabilityPayload(aulaId, disponibile, formatTimestamp(inizioDateTime) + " - " + formatTimestamp(fineDateTime)),
                                sessionId),
            HttpStatus.OK
        );
    }

    // Stato attuale di un'aula.
    // Path "/stato-aula/{aulaId}" e non "/stato/{aulaId}": quest'ultimo collideva con
    // "/stato/{stato}" (prenotazioni per stato) piu' sotto. Essendo lo stesso pattern di
    // path, Spring li registrava entrambi ma a runtime falliva con "Ambiguous handler
    // methods mapped", quindi ENTRAMBI gli endpoint rispondevano 500. Nessun client
    // funzionante poteva dipendere dal vecchio path, per questo il rename e' sicuro.
    @GetMapping("/stato-aula/{aulaId}")
    @Operation(summary = "Stato attuale di un'aula")
    public ResponseEntity<RoomStatusPayload> getStatoAula(@PathVariable Long aulaId) {
        logger.debug("INIZIO getStatoAula - AulaId: {}", aulaId);
        String stato = prenotazioneService.getStatoAula(aulaId, LocalDateTime.now());
        logger.debug("FINE getStatoAula - AulaId: {}, Stato: {}", aulaId, stato);
        return ResponseEntity.ok(new RoomStatusPayload(aulaId, stato, LocalDateTime.now()));
    }

    // Lista prenotazioni utente - ESCLUDE automaticamente le prenotazioni annullate
    @GetMapping("/mie")
    @Operation(summary = "Le prenotazioni dell'utente autenticato")
    public ResponseEntity<SinglePrenotazioniPayload> getMiePrenotazioni(@AuthenticationPrincipal AppPrincipal principal) {
        logger.debug("INIZIO getMiePrenotazioni");
        List<Prenotazione> tuttePrenotazioni = prenotazioneService.getPrenotazioniUtente(principal.id());

        List<Prenotazione> prenotazioni = tuttePrenotazioni.stream()
            .filter(p -> !"annullata".equalsIgnoreCase(p.getStato()))
            .collect(Collectors.toList());

        logger.debug("FINE getMiePrenotazioni - Prenotazioni attive recuperate per utente: {}, totale: {} (escluse {} annullate)",
                   principal.id(), prenotazioni.size(), tuttePrenotazioni.size() - prenotazioni.size());
        return ResponseEntity.ok(new SinglePrenotazioniPayload(prenotazioni));
    }

    // Annulla prenotazione
    @DeleteMapping("/{prenotazioneId}")
    @Operation(summary = "Annulla una prenotazione (solo proprietario o admin)")
    public ResponseEntity<ApiEnvelope<CancellationAckPayload>> annullaPrenotazione(@PathVariable Long prenotazioneId,
                                                @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO annullaPrenotazione - PrenotazioneId: {}", sessionId, prenotazioneId);

        Prenotazione prenotazioneEsistente = prenotazioneService.getPrenotazioneById(prenotazioneId);
        if (prenotazioneEsistente == null) {
            logger.warn("[{}] FINE annullaPrenotazione - Prenotazione non trovata: ID {}", sessionId, prenotazioneId);
            return new ResponseEntity<>(
                createErrorResponse("PRENOTAZIONE_NOT_FOUND", "Prenotazione non trovata",
                                  "La prenotazione che stai cercando di annullare non esiste.", sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("[{}] Prenotazione trovata: ID {}, proprietario: {}, stato: {}",
                    sessionId, prenotazioneId, prenotazioneEsistente.getUtente().getId(), prenotazioneEsistente.getStato());

        boolean annullata = prenotazioneService.annullaPrenotazione(prenotazioneId, principal.id());

        if (!annullata) {
            if (!prenotazioneEsistente.getUtente().getId().equals(principal.id())) {
                logger.warn("[{}] FINE annullaPrenotazione - Tentativo di annullare prenotazione di altro utente | PrenotazioneId: {} | Proprietario: {} | Richiedente: {}",
                           sessionId, prenotazioneId, prenotazioneEsistente.getUtente().getId(), principal.id());
                return new ResponseEntity<>(
                    createErrorResponse("ACCESS_DENIED", "Accesso negato",
                                      "Puoi annullare solo le tue prenotazioni.", sessionId),
                    HttpStatus.FORBIDDEN
                );
            }
            if (!"prenotata".equalsIgnoreCase(prenotazioneEsistente.getStato())) {
                logger.warn("[{}] FINE annullaPrenotazione - Tentativo di annullare prenotazione con stato non valido | PrenotazioneId: {} | Stato: {}",
                           sessionId, prenotazioneId, prenotazioneEsistente.getStato());
                return new ResponseEntity<>(
                    createErrorResponse("INVALID_STATE", "Stato prenotazione non valido",
                                      "Puoi annullare solo prenotazioni attive. Questa prenotazione è nello stato: " + prenotazioneEsistente.getStato(), sessionId),
                    HttpStatus.CONFLICT
                );
            }
            logger.warn("[{}] FINE annullaPrenotazione - Annullamento fallito per motivo sconosciuto | PrenotazioneId: {} | UtenteId: {}",
                       sessionId, prenotazioneId, principal.id());
            return new ResponseEntity<>(
                createErrorResponse("CANCELLATION_FAILED", "Impossibile annullare la prenotazione",
                                  "La prenotazione non può essere annullata al momento. Riprova più tardi.", sessionId),
                HttpStatus.CONFLICT
            );
        }

        logger.debug("[{}] FINE annullaPrenotazione - Prenotazione annullata con successo | PrenotazioneId: {} | UtenteId: {}",
                   sessionId, prenotazioneId, principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione annullata con successo",
                                new CancellationAckPayload(prenotazioneId, principal.id(), formatTimestamp(LocalDateTime.now())),
                                sessionId),
            HttpStatus.OK
        );
    }

    // Lista tutte le prenotazioni (semplice) - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    // ESCLUDE automaticamente le prenotazioni annullate
    @GetMapping
    @Operation(summary = "Elenca tutte le prenotazioni attive (PII del proprietario rimossa)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SinglePrenotazioniPayload.class)))
    public ResponseEntity<?> getAllPrenotazioni() {
        logger.debug("INIZIO getAllPrenotazioni");

        List<Prenotazione> tuttePrenotazioni = prenotazioneService.getAllPrenotazioni();
        List<Prenotazione> prenotazioni = tuttePrenotazioni.stream()
            .filter(p -> !"annullata".equalsIgnoreCase(p.getStato()))
            .map(this::sanitizeOwnerForListing)
            .collect(Collectors.toList());

        if (prenotazioni.isEmpty()) {
            logger.debug("FINE getAllPrenotazioni - Nessuna prenotazione attiva trovata");
            return ResponseEntity.ok(new MessageResponse("Nessuna prenotazione attiva trovata"));
        }

        logger.debug("FINE getAllPrenotazioni - Prenotazioni attive recuperate: {} (totale con annullate: {})",
                   prenotazioni.size(), tuttePrenotazioni.size());
        return ResponseEntity.ok(new SinglePrenotazioniPayload(prenotazioni));
    }

    // Singola prenotazione per ID (semplice) - SOLO IL PROPRIETARIO O UN ADMIN
    @GetMapping("/{id}")
    @PreAuthorize("@prenotazioneAuth.isOwnerOrAdmin(#id, principal)")
    @Operation(summary = "Recupera una singola prenotazione (solo proprietario o admin)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = PrenotazioneWrapper.class)))
    public ResponseEntity<?> getPrenotazioneById(@PathVariable Long id) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO getPrenotazioneById - ID Prenotazione: {}", sessionId, id);

        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            logger.debug("[{}] FINE getPrenotazioneById - Prenotazione non trovata con ID: {}", sessionId, id);
            return new ResponseEntity<>(new SimpleErrorResponse("Prenotazione non trovata"), HttpStatus.NOT_FOUND);
        }

        logger.debug("[{}] FINE getPrenotazioneById - Prenotazione recuperata con successo: ID: {}", sessionId, prenotazione.getId());
        return ResponseEntity.ok(new PrenotazioneWrapper(prenotazione));
    }

    // Dettagli completi di una prenotazione specifica - SOLO IL PROPRIETARIO O UN ADMIN
    @GetMapping("/{id}/details")
    @PreAuthorize("@prenotazioneAuth.isOwnerOrAdmin(#id, principal)")
    @Operation(summary = "Dettagli completi di una prenotazione (solo proprietario o admin)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = PrenotazioneWithDettagliPayload.class)))
    public ResponseEntity<?> getPrenotazioneDetailsById(@PathVariable Long id) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO getPrenotazioneDetailsById - ID Prenotazione: {}", sessionId, id);

        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            logger.debug("[{}] FINE getPrenotazioneDetailsById - Prenotazione non trovata con ID: {}", sessionId, id);
            return new ResponseEntity<>(new SimpleErrorResponse("Prenotazione non trovata"), HttpStatus.NOT_FOUND);
        }

        logger.debug("Prenotazione trovata: ID: {}", prenotazione.getId());
        List<PrenotazioneDettaglioDto> dettagliCompleti = prenotazioneService.getPrenotazioneCompleteDetails(id);
        logger.debug("FINE getPrenotazioneDetailsById - Dettagli completi recuperati con successo, totale dettagli: {}", dettagliCompleti.size());
        return ResponseEntity.ok(new PrenotazioneWithDettagliPayload(prenotazione, dettagliCompleti));
    }

    // Vista completa di tutte le prenotazioni con dettagli - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/all-details")
    @Operation(summary = "Dettagli completi di tutte le prenotazioni")
    public ResponseEntity<PrenotazioneDettaglioListPayload> getAllPrenotazioniWithDetails() {
        logger.debug("INIZIO getAllPrenotazioniWithDetails");
        List<PrenotazioneDettaglioDto> dettagliCompleti = prenotazioneService.getAllCompleteDetails();
        logger.debug("FINE getAllPrenotazioniWithDetails - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return ResponseEntity.ok(new PrenotazioneDettaglioListPayload(dettagliCompleti));
    }

    // Prenotazioni per stato - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/stato/{stato}")
    @Operation(summary = "Elenca le prenotazioni per stato")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = PrenotazioniByStatoPayload.class)))
    public ResponseEntity<?> getPrenotazioniByStato(@PathVariable String stato) {
        logger.debug("INIZIO getPrenotazioniByStato - Stato: {}", stato);
        try {
            List<Prenotazione> prenotazioni = prenotazioneService.getPrenotazioniByStato(stato.toLowerCase())
                .stream().map(this::sanitizeOwnerForListing).collect(Collectors.toList());

            logger.debug("FINE getPrenotazioniByStato - Prenotazioni recuperate con successo per stato: {}, totale: {}", stato, prenotazioni.size());
            return ResponseEntity.ok(new PrenotazioniByStatoPayload(stato, prenotazioni));
        } catch (IllegalArgumentException e) {
            logger.debug("FINE getPrenotazioniByStato - Stato non valido: {}", stato);
            return new ResponseEntity<>(
                new SimpleErrorResponse("Stato non valido. Stati disponibili: PRENOTATA, BLOCCATA, MANUTENZIONE, ANNULLATA"),
                HttpStatus.BAD_REQUEST
            );
        }
    }

    // Prenotazioni future - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/future")
    @Operation(summary = "Elenca le prenotazioni future")
    public ResponseEntity<PrenotazioniListWithTotalPayload> getPrenotazioniFuture() {
        logger.debug("INIZIO getPrenotazioniFuture");
        List<Prenotazione> prenotazioni = prenotazioneService.getPrenotazioniFuture()
            .stream().map(this::sanitizeOwnerForListing).collect(Collectors.toList());
        logger.debug("FINE getPrenotazioniFuture - Prenotazioni future recuperate con successo, totale: {}", prenotazioni.size());
        return ResponseEntity.ok(new PrenotazioniListWithTotalPayload(prenotazioni));
    }
}
