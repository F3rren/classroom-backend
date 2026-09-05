package com.prenotazioni.booking.controller;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.exception.InvalidRequestException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.exception.ResourceType;
import com.prenotazioni.dto.*;
// entrambe: in com.prenotazioni.dto restano le classi comuni di shared,
// in com.prenotazioni.booking.dto quelle di questo servizio
import com.prenotazioni.booking.dto.*;
import com.prenotazioni.exception.BookingConflictException;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingOwner;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.booking.service.BookingService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Prenotazioni")
public class BookingController {

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    private final BookingService bookingService;

    BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * L'istantanea di chi sta prenotando, presa dai claim del token.
     *
     * Prima veniva letta dalla tabella utenti. Quella tabella ora appartiene ad
     * auth-service: leggerla richiederebbe una chiamata di rete a ogni prenotazione,
     * e il token porta gia' esattamente questi tre campi.
     */
    private static BookingOwner istantaneaDi(AppPrincipal principal) {
        return new BookingOwner(principal.id(), principal.username(), principal.name());
    }

    /** Lo stesso identificativo che vedra' il gestore degli errori, non uno diverso. */
    private String generateSessionId() {
        return RequestCorrelationFilter.current();
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
    private Booking sanitizeOwnerForListing(Booking p) {
        // Non c'e' piu' nulla da rimuovere: la prenotazione conserva solo id, username e
        // nome, cioe' esattamente i campi che questo metodo ricopiava a mano. Email, ruolo
        // e date di accesso non sono piu' nemmeno raggiungibili da qui.
        return p;
    }

    // Prenota un'aula
    @PostMapping("/book")
    @Operation(summary = "Prenota un'aula")
    public ResponseEntity<ApiEnvelope<BookingAckPayload>> bookRoom(@Valid @RequestBody BookingRequest request,
                                        @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO prenotaAula - AulaId: {}, CorsoId: {}, Periodo: {} - {}", request.getRoomId(), request.getCourseId(), request.getStartTime(), request.getEndTime());

        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.parse(request.getStartTime());
        } catch (DateTimeParseException e) {
            logger.warn("FINE prenotaAula - Errore parsing data inizio: '{}'", request.getStartTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            endTime = LocalDateTime.parse(request.getEndTime());
        } catch (DateTimeParseException e) {
            logger.warn("FINE prenotaAula - Errore parsing data fine: '{}'", request.getEndTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (endTime.isBefore(startTime)) {
            logger.warn("FINE prenotaAula - Data fine precedente alla data inizio");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (startTime.isBefore(LocalDateTime.now())) {
            logger.warn("FINE prenotaAula - Tentativo di prenotazione nel passato: {}", formatTimestamp(startTime));
            return new ResponseEntity<>(
                createErrorResponse("PAST_DATE", "Date in the past",
                                  "Non puoi prenotare un'aula per una data già trascorsa.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("Validazioni superate, tentativo prenotazione per periodo: {} - {}", formatTimestamp(startTime), formatTimestamp(endTime));

        Booking booking;
        try {
            booking = bookingService.bookRoom(
                request.getRoomId(), request.getCourseId(), istantaneaDi(principal), startTime, endTime, request.getDescription());
        } catch (DataIntegrityViolationException e) {
            logger.warn("FINE prenotaAula - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - AulaId: {}", request.getRoomId());
            throw new BookingConflictException("BOOKING_CONFLICT", "Impossibile prenotare l'aula",
                    "L'aula è appena stata prenotata da un'altra richiesta per lo stesso periodo. Riprova con un altro orario.");
        }

        logger.debug("FINE prenotaAula - Prenotazione creata con successo - ID: {}, AulaId: {}, UtenteId: {}", booking.getId(), request.getRoomId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione effettuata con successo",
                                new BookingAckPayload(booking, request.getRoomId(), formatTimestamp(startTime) + " - " + formatTimestamp(endTime)),
                                sessionId),
            HttpStatus.CREATED
        );
    }

    // Modifica una prenotazione esistente
    @PutMapping("/{bookingId}")
    @Operation(summary = "Modifica una prenotazione esistente (solo proprietario o admin)")
    public ResponseEntity<ApiEnvelope<BookingAckPayload>> editBooking(@PathVariable("bookingId") Long bookingId,
                                                 @Valid @RequestBody BookingRequest request,
                                                 @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO modificaPrenotazione - PrenotazioneId: {}, AulaId: {}, CorsoId: {}, Periodo: {} - {}", bookingId, request.getRoomId(), request.getCourseId(), request.getStartTime(), request.getEndTime());

        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.parse(request.getStartTime());
        } catch (DateTimeParseException e) {
            logger.warn("FINE modificaPrenotazione - Errore parsing data inizio: '{}'", request.getStartTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            endTime = LocalDateTime.parse(request.getEndTime());
        } catch (DateTimeParseException e) {
            logger.warn("FINE modificaPrenotazione - Errore parsing data fine: '{}'", request.getEndTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (endTime.isBefore(startTime)) {
            logger.warn("FINE modificaPrenotazione - Data fine precedente alla data inizio");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (startTime.isBefore(LocalDateTime.now())) {
            logger.warn("FINE modificaPrenotazione - Tentativo di modifica con data nel passato: {}", formatTimestamp(startTime));
            return new ResponseEntity<>(
                createErrorResponse("PAST_DATE", "Date in the past",
                                  "Non puoi modificare una prenotazione per una data già trascorsa.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("Validazioni superate, tentativo modifica prenotazione ID {} per periodo: {} - {}", bookingId, formatTimestamp(startTime), formatTimestamp(endTime));

        Booking booking;
        try {
            booking = bookingService.updateBooking(
                bookingId, request.getRoomId(), request.getCourseId(), principal.id(), principal.isAdmin(), startTime, endTime, request.getDescription());
        } catch (DataIntegrityViolationException e) {
            logger.warn("FINE modificaPrenotazione - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - PrenotazioneId: {}, AulaId: {}", bookingId, request.getRoomId());
            throw new BookingConflictException("UPDATE_CONFLICT", "Impossibile modificare la prenotazione",
                    "L'aula è appena stata prenotata da un'altra richiesta per il nuovo periodo. Riprova con un altro orario.");
        }

        logger.debug("FINE modificaPrenotazione - Prenotazione modificata con successo - ID: {}, AulaId: {}, UtenteId: {}", booking.getId(), request.getRoomId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione modificata con successo",
                                new BookingAckPayload(booking, request.getRoomId(), formatTimestamp(startTime) + " - " + formatTimestamp(endTime)),
                                sessionId),
            HttpStatus.OK
        );
    }

    // Blocca un'aula (solo admin)
    @PostMapping("/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Blocca un'aula per un periodo (solo admin)")
    public ResponseEntity<ApiEnvelope<BlockAckPayload>> blockRoom(@Valid @RequestBody BookingRequest request,
                                       @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO bloccaAula - AulaId: {}, Periodo: {} - {}", request.getRoomId(), request.getStartTime(), request.getEndTime());

        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.parse(request.getStartTime());
        } catch (DateTimeParseException e) {
            logger.warn("FINE bloccaAula - Errore parsing data inizio: '{}'", request.getStartTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            endTime = LocalDateTime.parse(request.getEndTime());
        } catch (DateTimeParseException e) {
            logger.warn("FINE bloccaAula - Errore parsing data fine: '{}'", request.getEndTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (endTime.isBefore(startTime)) {
            logger.warn("FINE bloccaAula - Data fine precedente alla data inizio");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("Validazioni superate, tentativo blocco aula per periodo: {} - {}", formatTimestamp(startTime), formatTimestamp(endTime));

        Booking blocco;
        try {
            blocco = bookingService.blockRoom(request.getRoomId(), istantaneaDi(principal), startTime, endTime, request.getDescription());
        } catch (DataIntegrityViolationException e) {
            logger.warn("FINE bloccaAula - Conflitto rilevato dal vincolo del database (prenotazione concorrente) - AulaId: {}", request.getRoomId());
            throw new BookingConflictException("BLOCK_CONFLICT", "Impossibile bloccare l'aula",
                    "L'aula è appena stata occupata da un'altra richiesta per lo stesso periodo.");
        }

        logger.debug("FINE bloccaAula - Aula bloccata con successo - ID blocco: {}, AulaId: {}, Admin: {}", blocco.getId(), request.getRoomId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Aula bloccata con successo",
                                new BlockAckPayload(blocco, request.getRoomId(), formatTimestamp(startTime) + " - " + formatTimestamp(endTime), principal.id()),
                                sessionId),
            HttpStatus.CREATED
        );
    }

    // Verifica disponibilità aula
    @GetMapping("/availability")
    @Operation(summary = "Verifica la disponibilità di un'aula in un periodo")
    public ResponseEntity<ApiEnvelope<AvailabilityPayload>> checkAvailability(@RequestParam("roomId") Long roomId,
                                                   @RequestParam("start") String startTime,
                                                   @RequestParam("end") String endTime) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO verificaDisponibilita - AulaId: {}, Periodo: {} - {}", roomId, startTime, endTime);

        LocalDateTime startDateTime;
        LocalDateTime endDateTime;
        try {
            startDateTime = LocalDateTime.parse(startTime);
        } catch (DateTimeParseException e) {
            logger.warn("FINE verificaDisponibilita - Errore parsing data inizio: '{}'", startTime);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            endDateTime = LocalDateTime.parse(endTime);
        } catch (DateTimeParseException e) {
            logger.warn("FINE verificaDisponibilita - Errore parsing data fine: '{}'", endTime);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (endDateTime.isBefore(startDateTime)) {
            logger.warn("FINE verificaDisponibilita - Data fine precedente alla data inizio");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("Verifica disponibilità per AulaId: {} nel periodo: {} - {}", roomId, formatTimestamp(startDateTime), formatTimestamp(endDateTime));
        boolean available = bookingService.isRoomAvailable(roomId, startDateTime, endDateTime);
        logger.debug("FINE verificaDisponibilita - AulaId: {}, Disponibile: {}", roomId, available);

        return new ResponseEntity<>(
            createSuccessResponse("Verifica disponibilità completata",
                                new AvailabilityPayload(roomId, available, formatTimestamp(startDateTime) + " - " + formatTimestamp(endDateTime)),
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
    @GetMapping("/room-status/{roomId}")
    @Operation(summary = "Stato attuale di un'aula")
    public ResponseEntity<RoomStatusPayload> getRoomStatus(@PathVariable("roomId") Long roomId) {
        logger.debug("INIZIO getStatoAula - AulaId: {}", roomId);
        String status = bookingService.getRoomStatus(roomId, LocalDateTime.now());
        logger.debug("FINE getStatoAula - AulaId: {}, Stato: {}", roomId, status);
        return ResponseEntity.ok(new RoomStatusPayload(roomId, status, LocalDateTime.now()));
    }

    // Lista prenotazioni utente - ESCLUDE automaticamente le prenotazioni annullate
    @GetMapping("/mine")
    @Operation(summary = "Le prenotazioni dell'utente autenticato")
    public ResponseEntity<SingleBookingPayload> getMyBookings(@AuthenticationPrincipal AppPrincipal principal) {
        logger.debug("INIZIO getMiePrenotazioni");
        List<Booking> allBookings = bookingService.getUserBookings(principal.id());

        List<Booking> bookings = allBookings.stream()
            .filter(p -> p.getStatus() != BookingStatus.CANCELLED)
            .collect(Collectors.toList());

        logger.debug("FINE getMiePrenotazioni - Prenotazioni attive recuperate per utente: {}, totale: {} (escluse {} annullate)",
                   principal.id(), bookings.size(), allBookings.size() - bookings.size());
        return ResponseEntity.ok(new SingleBookingPayload(bookings));
    }

    // Annulla prenotazione
    @DeleteMapping("/{bookingId}")
    @Operation(summary = "Annulla una prenotazione (solo proprietario o admin)")
    public ResponseEntity<ApiEnvelope<CancellationAckPayload>> cancelBooking(@PathVariable("bookingId") Long bookingId,
                                                @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO annullaPrenotazione - PrenotazioneId: {}", bookingId);


        // Nessun controllo sull'esito, e soprattutto nessuna ricostruzione del perche':
        // il service lancia gia' AccessDeniedException per il proprietario sbagliato,
        // DomainConflictException per lo stato non annullabile e ResourceNotFoundException
        // se non esiste. Prima questo blocco RIFACEVA quei controlli per interpretare un
        // booleano, e un commento avvertiva di tenerne l'ordine allineato a quello del
        // service: due copie della stessa regola da sincronizzare a mano.
        bookingService.cancelBooking(bookingId, principal.id(), principal.isAdmin());


        logger.debug("FINE annullaPrenotazione - Prenotazione annullata con successo | PrenotazioneId: {} | UtenteId: {}", bookingId, principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione annullata con successo",
                                new CancellationAckPayload(bookingId, principal.id(), formatTimestamp(LocalDateTime.now())),
                                sessionId),
            HttpStatus.OK
        );
    }

    // Lista tutte le prenotazioni (semplice) - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    // ESCLUDE automaticamente le prenotazioni annullate
    @GetMapping
    @Operation(summary = "Elenca tutte le prenotazioni attive (PII del proprietario rimossa)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SingleBookingPayload.class)))
    public ResponseEntity<?> getAllBookings() {
        logger.debug("INIZIO getAllPrenotazioni");

        List<Booking> allBookings = bookingService.getAllBookings();
        List<Booking> bookings = allBookings.stream()
            .filter(p -> p.getStatus() != BookingStatus.CANCELLED)
            .map(this::sanitizeOwnerForListing)
            .collect(Collectors.toList());

        if (bookings.isEmpty()) {
            logger.debug("FINE getAllPrenotazioni - Nessuna prenotazione attiva trovata");
            return ResponseEntity.ok(new MessageResponse("Nessuna prenotazione attiva trovata"));
        }

        logger.debug("FINE getAllPrenotazioni - Prenotazioni attive recuperate: {} (totale con annullate: {})",
                   bookings.size(), allBookings.size());
        return ResponseEntity.ok(new SingleBookingPayload(bookings));
    }

    // Singola prenotazione per ID (semplice) - SOLO IL PROPRIETARIO O UN ADMIN
    @GetMapping("/{id}")
    @PreAuthorize("@prenotazioneAuth.isOwnerOrAdmin(#id, principal)")
    @Operation(summary = "Recupera una singola prenotazione (solo proprietario o admin)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = BookingWrapper.class)))
    public ResponseEntity<?> getBookingById(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getPrenotazioneById - ID Prenotazione: {}", id);

        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            // Prima: {"error":"Prenotazione non trovata"} - nessun "success", nessun
            // "userMessage", e "error" conteneva una frase invece di un codice. Un client
            // che legge userMessage otteneva undefined proprio su questi due endpoint.
            throw ResourceNotFoundException.forId(ResourceType.BOOKING, id);
        }

        logger.debug("FINE getPrenotazioneById - Prenotazione recuperata con successo: ID: {}", booking.getId());
        return ResponseEntity.ok(new BookingWrapper(booking));
    }

    // Dettagli completi di una prenotazione specifica - SOLO IL PROPRIETARIO O UN ADMIN
    @GetMapping("/{id}/details")
    @PreAuthorize("@prenotazioneAuth.isOwnerOrAdmin(#id, principal)")
    @Operation(summary = "Dettagli completi di una prenotazione (solo proprietario o admin)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = BookingWithDetailsPayload.class)))
    public ResponseEntity<?> getBookingDetailsById(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getPrenotazioneDetailsById - ID Prenotazione: {}", id);

        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            // Prima: {"error":"Prenotazione non trovata"} - nessun "success", nessun
            // "userMessage", e "error" conteneva una frase invece di un codice. Un client
            // che legge userMessage otteneva undefined proprio su questi due endpoint.
            throw ResourceNotFoundException.forId(ResourceType.BOOKING, id);
        }

        logger.debug("Prenotazione trovata: ID: {}", booking.getId());
        List<BookingDetailDto> fullDetails = bookingService.getBookingCompleteDetails(id);
        logger.debug("FINE getPrenotazioneDetailsById - Dettagli completi recuperati con successo, totale dettagli: {}", fullDetails.size());
        return ResponseEntity.ok(new BookingWithDetailsPayload(booking, fullDetails));
    }

    // Vista completa di tutte le prenotazioni con dettagli - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/all-details")
    @Operation(summary = "Dettagli completi di tutte le prenotazioni")
    public ResponseEntity<BookingDetailListPayload> getAllBookingsWithDetails() {
        logger.debug("INIZIO getAllPrenotazioniWithDetails");
        List<BookingDetailDto> fullDetails = bookingService.getAllCompleteDetails();
        logger.debug("FINE getAllPrenotazioniWithDetails - Dettagli completi recuperati con successo, totale prenotazioni: {}", fullDetails.size());
        return ResponseEntity.ok(new BookingDetailListPayload(fullDetails));
    }

    // Prenotazioni per stato - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/status/{status}")
    @Operation(summary = "Elenca le prenotazioni per stato")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = BookingsByStatusPayload.class)))
    public ResponseEntity<?> getBookingsByStatus(@PathVariable("status") String status) {
        logger.debug("INIZIO getPrenotazioniByStato - Stato: {}", status);
        try {
            List<Booking> bookings = bookingService.getBookingsByStatus(status.toLowerCase())
                .stream().map(this::sanitizeOwnerForListing).collect(Collectors.toList());

            logger.debug("FINE getPrenotazioniByStato - Prenotazioni recuperate con successo per stato: {}, totale: {}", status, bookings.size());
            return ResponseEntity.ok(new BookingsByStatusPayload(status, bookings));
        } catch (IllegalArgumentException e) {
            logger.debug("FINE getPrenotazioniByStato - Stato non valido: {}", status);
            // Uno stato inesistente e' un dato non valido, quindi 400 con l'envelope
            // comune. L'elenco degli stati ammessi si ricava dall'enum invece di essere
            // scritto a mano: la versione scritta a mano si sarebbe scollata al primo
            // valore aggiunto, e nessuno se ne sarebbe accorto.
            throw new InvalidRequestException("INVALID_STATE",
                    "Invalid state: " + status
                            + ". Allowed: " + java.util.Arrays.stream(BookingStatus.values())
                            .map(BookingStatus::getValue).collect(Collectors.joining(", ")),
                    "Stato non riconosciuto. Ammessi: " + java.util.Arrays.stream(BookingStatus.values())
                            .map(BookingStatus::getValue).collect(Collectors.joining(", ")));
        }
    }

    // Prenotazioni future - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/future")
    @Operation(summary = "Elenca le prenotazioni future")
    public ResponseEntity<BookingsListWithTotalPayload> getFutureBookings() {
        logger.debug("INIZIO getPrenotazioniFuture");
        List<Booking> bookings = bookingService.getFutureBookings()
            .stream().map(this::sanitizeOwnerForListing).collect(Collectors.toList());
        logger.debug("FINE getPrenotazioniFuture - Prenotazioni future recuperate con successo, totale: {}", bookings.size());
        return ResponseEntity.ok(new BookingsListWithTotalPayload(bookings));
    }
}
