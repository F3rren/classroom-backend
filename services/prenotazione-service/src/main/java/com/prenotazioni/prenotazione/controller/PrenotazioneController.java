package com.prenotazioni.prenotazione.controller;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.exception.InvalidRequestException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.dto.*;
// entrambe: in com.prenotazioni.dto restano le classi comuni di shared,
// in com.prenotazioni.prenotazione.dto quelle di questo servizio
import com.prenotazioni.prenotazione.dto.*;
import com.prenotazioni.exception.BookingConflictException;
import com.prenotazioni.prenotazione.model.Prenotazione;
import com.prenotazioni.prenotazione.model.ProprietarioPrenotazione;
import com.prenotazioni.prenotazione.model.StatoPrenotazione;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.prenotazione.service.PrenotazioneService;
import com.prenotazioni.util.Timestamps;

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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/prenotazioni")
@Tag(name = "Prenotazioni")
public class PrenotazioneController {

    private static final Logger logger = LoggerFactory.getLogger(PrenotazioneController.class);

    private final PrenotazioneService prenotazioneService;

    PrenotazioneController(PrenotazioneService prenotazioneService) {
        this.prenotazioneService = prenotazioneService;
    }

    /**
     * L'istantanea di chi sta prenotando, presa dai claim del token.
     *
     * Prima veniva letta dalla tabella utenti. Quella tabella ora appartiene ad
     * auth-service: leggerla richiederebbe una chiamata di rete a ogni prenotazione,
     * e il token porta gia' esattamente questi tre campi.
     */
    private static ProprietarioPrenotazione istantaneaDi(AppPrincipal principal) {
        return new ProprietarioPrenotazione(principal.id(), principal.username(), principal.nome());
    }

    /** Lo stesso identificativo che vedra' il gestore degli errori, non uno diverso. */
    private String generateSessionId() {
        return RequestCorrelationFilter.corrente();
    }

    private String formatTimestamp(LocalDateTime dateTime) {
        return Timestamps.format(dateTime);
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
        // Non c'e' piu' nulla da rimuovere: la prenotazione conserva solo id, username e
        // nome, cioe' esattamente i campi che questo metodo ricopiava a mano. Email, ruolo
        // e date di accesso non sono piu' nemmeno raggiungibili da qui.
        return p;
    }

    // Prenota un'aula
    @PostMapping("/prenota")
    @Operation(summary = "Prenota un'aula")
    public ResponseEntity<ApiEnvelope<BookingAckPayload>> prenotaAula(@Valid @RequestBody PrenotazioneRequest request,
                                        @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO prenotaAula - AulaId: {}, CorsoId: {}, Periodo: {} - {}", request.getAulaId(), request.getCorsoId(), request.getInizio(), request.getFine());

        LocalDateTime inizio;
        LocalDateTime fine;
        try {
            inizio = LocalDateTime.parse(request.getInizio());
        } catch (DateTimeParseException e) {
            logger.warn("FINE prenotaAula - Errore parsing data inizio: '{}'", request.getInizio());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            fine = LocalDateTime.parse(request.getFine());
        } catch (DateTimeParseException e) {
            logger.warn("FINE prenotaAula - Errore parsing data fine: '{}'", request.getFine());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (fine.isBefore(inizio)) {
            logger.warn("FINE prenotaAula - Data fine precedente alla data inizio");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (inizio.isBefore(LocalDateTime.now())) {
            logger.warn("FINE prenotaAula - Tentativo di prenotazione nel passato: {}", formatTimestamp(inizio));
            return new ResponseEntity<>(
                createErrorResponse("PAST_DATE", "Date in the past",
                                  "Non puoi prenotare un'aula per una data già trascorsa.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("Validazioni superate, tentativo prenotazione per periodo: {} - {}", formatTimestamp(inizio), formatTimestamp(fine));

        Prenotazione prenotazione;
        try {
            prenotazione = prenotazioneService.prenotaAula(
                request.getAulaId(), request.getCorsoId(), istantaneaDi(principal), inizio, fine, request.getDescrizione());
        } catch (DataIntegrityViolationException e) {
            logger.warn("FINE prenotaAula - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - AulaId: {}", request.getAulaId());
            throw new BookingConflictException("BOOKING_CONFLICT", "Impossibile prenotare l'aula",
                    "L'aula è appena stata prenotata da un'altra richiesta per lo stesso periodo. Riprova con un altro orario.");
        }

        logger.debug("FINE prenotaAula - Prenotazione creata con successo - ID: {}, AulaId: {}, UtenteId: {}", prenotazione.getId(), request.getAulaId(), principal.id());
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
        logger.debug("INIZIO modificaPrenotazione - PrenotazioneId: {}, AulaId: {}, CorsoId: {}, Periodo: {} - {}", prenotazioneId, request.getAulaId(), request.getCorsoId(), request.getInizio(), request.getFine());

        LocalDateTime inizio;
        LocalDateTime fine;
        try {
            inizio = LocalDateTime.parse(request.getInizio());
        } catch (DateTimeParseException e) {
            logger.warn("FINE modificaPrenotazione - Errore parsing data inizio: '{}'", request.getInizio());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            fine = LocalDateTime.parse(request.getFine());
        } catch (DateTimeParseException e) {
            logger.warn("FINE modificaPrenotazione - Errore parsing data fine: '{}'", request.getFine());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (fine.isBefore(inizio)) {
            logger.warn("FINE modificaPrenotazione - Data fine precedente alla data inizio");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (inizio.isBefore(LocalDateTime.now())) {
            logger.warn("FINE modificaPrenotazione - Tentativo di modifica con data nel passato: {}", formatTimestamp(inizio));
            return new ResponseEntity<>(
                createErrorResponse("PAST_DATE", "Date in the past",
                                  "Non puoi modificare una prenotazione per una data già trascorsa.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("Validazioni superate, tentativo modifica prenotazione ID {} per periodo: {} - {}", prenotazioneId, formatTimestamp(inizio), formatTimestamp(fine));

        Prenotazione prenotazione;
        try {
            prenotazione = prenotazioneService.updatePrenotazione(
                prenotazioneId, request.getAulaId(), request.getCorsoId(), principal.id(), principal.isAdmin(), inizio, fine, request.getDescrizione());
        } catch (DataIntegrityViolationException e) {
            logger.warn("FINE modificaPrenotazione - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - PrenotazioneId: {}, AulaId: {}", prenotazioneId, request.getAulaId());
            throw new BookingConflictException("UPDATE_CONFLICT", "Impossibile modificare la prenotazione",
                    "L'aula è appena stata prenotata da un'altra richiesta per il nuovo periodo. Riprova con un altro orario.");
        }

        logger.debug("FINE modificaPrenotazione - Prenotazione modificata con successo - ID: {}, AulaId: {}, UtenteId: {}", prenotazione.getId(), request.getAulaId(), principal.id());
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
        logger.debug("INIZIO bloccaAula - AulaId: {}, Periodo: {} - {}", request.getAulaId(), request.getInizio(), request.getFine());

        LocalDateTime inizio;
        LocalDateTime fine;
        try {
            inizio = LocalDateTime.parse(request.getInizio());
        } catch (DateTimeParseException e) {
            logger.warn("FINE bloccaAula - Errore parsing data inizio: '{}'", request.getInizio());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            fine = LocalDateTime.parse(request.getFine());
        } catch (DateTimeParseException e) {
            logger.warn("FINE bloccaAula - Errore parsing data fine: '{}'", request.getFine());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (fine.isBefore(inizio)) {
            logger.warn("FINE bloccaAula - Data fine precedente alla data inizio");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("Validazioni superate, tentativo blocco aula per periodo: {} - {}", formatTimestamp(inizio), formatTimestamp(fine));

        Prenotazione blocco;
        try {
            blocco = prenotazioneService.bloccaAula(request.getAulaId(), istantaneaDi(principal), inizio, fine, request.getDescrizione());
        } catch (DataIntegrityViolationException e) {
            logger.warn("FINE bloccaAula - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - AulaId: {}", request.getAulaId());
            throw new BookingConflictException("BLOCK_CONFLICT", "Impossibile bloccare l'aula",
                    "L'aula è appena stata occupata da un'altra richiesta per lo stesso periodo.");
        }

        logger.debug("FINE bloccaAula - Aula bloccata con successo - ID blocco: {}, AulaId: {}, Admin: {}", blocco.getId(), request.getAulaId(), principal.id());
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
        logger.debug("INIZIO verificaDisponibilita - AulaId: {}, Periodo: {} - {}", aulaId, inizio, fine);

        LocalDateTime inizioDateTime;
        LocalDateTime fineDateTime;
        try {
            inizioDateTime = LocalDateTime.parse(inizio);
        } catch (DateTimeParseException e) {
            logger.warn("FINE verificaDisponibilita - Errore parsing data inizio: '{}'", inizio);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            fineDateTime = LocalDateTime.parse(fine);
        } catch (DateTimeParseException e) {
            logger.warn("FINE verificaDisponibilita - Errore parsing data fine: '{}'", fine);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (fineDateTime.isBefore(inizioDateTime)) {
            logger.warn("FINE verificaDisponibilita - Data fine precedente alla data inizio");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("Verifica disponibilità per AulaId: {} nel periodo: {} - {}", aulaId, formatTimestamp(inizioDateTime), formatTimestamp(fineDateTime));
        boolean disponibile = prenotazioneService.isAulaDisponibile(aulaId, inizioDateTime, fineDateTime);
        logger.debug("FINE verificaDisponibilita - AulaId: {}, Disponibile: {}", aulaId, disponibile);

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
            .filter(p -> p.getStato() != StatoPrenotazione.ANNULLATA)
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
        logger.debug("INIZIO annullaPrenotazione - PrenotazioneId: {}", prenotazioneId);


        // Nessun controllo sull'esito, e soprattutto nessuna ricostruzione del perche':
        // il service lancia gia' AccessDeniedException per il proprietario sbagliato,
        // DomainConflictException per lo stato non annullabile e ResourceNotFoundException
        // se non esiste. Prima questo blocco RIFACEVA quei controlli per interpretare un
        // booleano, e un commento avvertiva di tenerne l'ordine allineato a quello del
        // service: due copie della stessa regola da sincronizzare a mano.
        prenotazioneService.annullaPrenotazione(prenotazioneId, principal.id(), principal.isAdmin());


        logger.debug("FINE annullaPrenotazione - Prenotazione annullata con successo | PrenotazioneId: {} | UtenteId: {}", prenotazioneId, principal.id());
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
            .filter(p -> p.getStato() != StatoPrenotazione.ANNULLATA)
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
        logger.debug("INIZIO getPrenotazioneById - ID Prenotazione: {}", id);

        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            // Prima: {"error":"Prenotazione non trovata"} - nessun "success", nessun
            // "userMessage", e "error" conteneva una frase invece di un codice. Un client
            // che legge userMessage otteneva undefined proprio su questi due endpoint.
            throw ResourceNotFoundException.perId("Prenotazione", "PRENOTAZIONE_NOT_FOUND", id);
        }

        logger.debug("FINE getPrenotazioneById - Prenotazione recuperata con successo: ID: {}", prenotazione.getId());
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
        logger.debug("INIZIO getPrenotazioneDetailsById - ID Prenotazione: {}", id);

        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            // Prima: {"error":"Prenotazione non trovata"} - nessun "success", nessun
            // "userMessage", e "error" conteneva una frase invece di un codice. Un client
            // che legge userMessage otteneva undefined proprio su questi due endpoint.
            throw ResourceNotFoundException.perId("Prenotazione", "PRENOTAZIONE_NOT_FOUND", id);
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
            // Uno stato inesistente e' un dato non valido, quindi 400 con l'envelope
            // comune. L'elenco degli stati ammessi si ricava dall'enum invece di essere
            // scritto a mano: la versione scritta a mano si sarebbe scollata al primo
            // valore aggiunto, e nessuno se ne sarebbe accorto.
            throw new InvalidRequestException("INVALID_STATE",
                    "Invalid state: " + stato
                            + ". Allowed: " + java.util.Arrays.stream(StatoPrenotazione.values())
                            .map(StatoPrenotazione::getValore).collect(Collectors.joining(", ")),
                    "Stato non riconosciuto. Ammessi: " + java.util.Arrays.stream(StatoPrenotazione.values())
                            .map(StatoPrenotazione::getValore).collect(Collectors.joining(", ")));
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
