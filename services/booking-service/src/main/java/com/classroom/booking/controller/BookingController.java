package com.classroom.booking.controller;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.exception.InvalidRequestException;
import com.classroom.exception.ResourceNotFoundException;
import com.classroom.exception.ResourceType;
import com.classroom.dto.*;
// both: com.classroom.dto keeps the classes shared holds in common,
// com.classroom.booking.dto the ones belonging to this service
import com.classroom.booking.dto.*;
import com.classroom.exception.BookingConflictException;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingOwner;
import com.classroom.booking.model.BookingStatus;
import com.classroom.security.AppPrincipal;
import com.classroom.booking.service.BookingService;
import com.classroom.util.Timestamps;

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
@Tag(name = "Bookings")
public class BookingController {

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    private final BookingService bookingService;

    BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * A snapshot of whoever is booking, taken from the token's claims.
     *
     * It used to be read from the users table. That table now belongs to auth-service:
     * reading it would mean a network call on every booking, and the token already carries
     * exactly these three fields.
     */
    private static BookingOwner snapshotOf(AppPrincipal principal) {
        return new BookingOwner(principal.id(), principal.username(), principal.name());
    }

    /** The same id the error handler will see, not a different one. */
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

    // Strips the owner's personal data from listings visible to every authenticated user
    // (keeps only id/username/name, never the email, role or login dates of somebody else)
    private Booking sanitizeOwnerForListing(Booking p) {
        // There is nothing left to strip: a booking keeps only id, username and name, which
        // are exactly the fields this method used to copy across by hand. The email, the role
        // and the login dates are not even reachable from here any more.
        return p;
    }

    // Books a room.
    @PostMapping("/book")
    @Operation(summary = "Book a room")
    public ResponseEntity<ApiEnvelope<BookingAckPayload>> bookRoom(@Valid @RequestBody BookingRequest request,
                                        @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("START prenotaroom - roomId: {}, courseId: {}, period: {} - {}", request.getRoomId(), request.getCourseId(), request.getStartTime(), request.getEndTime());

        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.parse(request.getStartTime());
        } catch (DateTimeParseException e) {
            logger.warn("END prenotaroom - could not parse the start date: '{}'", request.getStartTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            endTime = LocalDateTime.parse(request.getEndTime());
        } catch (DateTimeParseException e) {
            logger.warn("END prenotaroom - could not parse the end date: '{}'", request.getEndTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (endTime.isBefore(startTime)) {
            logger.warn("END prenotaroom - the end date is before the start date");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (startTime.isBefore(LocalDateTime.now())) {
            logger.warn("END prenotaroom - attempt to book in the past: {}", formatTimestamp(startTime));
            return new ResponseEntity<>(
                createErrorResponse("PAST_DATE", "Date in the past",
                                  "Non puoi prenotare un'aula per una data già trascorsa.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("validation passed, attempting a booking for the period: {} - {}", formatTimestamp(startTime), formatTimestamp(endTime));

        Booking booking;
        try {
            booking = bookingService.bookRoom(
                request.getRoomId(), request.getCourseId(), snapshotOf(principal), startTime, endTime, request.getDescription());
        } catch (DataIntegrityViolationException e) {
            logger.warn("END prenotaroom - conflict raised by the database constraint (concurrent booking) - roomId: {}", request.getRoomId());
            throw new BookingConflictException("BOOKING_CONFLICT", "Impossibile prenotare l'aula",
                    "L'aula è appena stata prenotata da un'altra richiesta per lo stesso periodo. Riprova con un altro orario.");
        }

        logger.debug("END prenotaroom - booking created - ID: {}, roomId: {}, userId: {}", booking.getId(), request.getRoomId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione effettuata con successo",
                                new BookingAckPayload(booking, request.getRoomId(), formatTimestamp(startTime) + " - " + formatTimestamp(endTime)),
                                sessionId),
            HttpStatus.CREATED
        );
    }

    // Updates an existing booking.
    @PutMapping("/{bookingId}")
    @Operation(summary = "Update an existing booking (owner or admin only)")
    public ResponseEntity<ApiEnvelope<BookingAckPayload>> editBooking(@PathVariable("bookingId") Long bookingId,
                                                 @Valid @RequestBody BookingRequest request,
                                                 @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("START updateBooking - bookingId: {}, roomId: {}, courseId: {}, period: {} - {}", bookingId, request.getRoomId(), request.getCourseId(), request.getStartTime(), request.getEndTime());

        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.parse(request.getStartTime());
        } catch (DateTimeParseException e) {
            logger.warn("END updateBooking - could not parse the start date: '{}'", request.getStartTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            endTime = LocalDateTime.parse(request.getEndTime());
        } catch (DateTimeParseException e) {
            logger.warn("END updateBooking - could not parse the end date: '{}'", request.getEndTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (endTime.isBefore(startTime)) {
            logger.warn("END updateBooking - the end date is before the start date");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (startTime.isBefore(LocalDateTime.now())) {
            logger.warn("END updateBooking - attempt to update with a date in the past: {}", formatTimestamp(startTime));
            return new ResponseEntity<>(
                createErrorResponse("PAST_DATE", "Date in the past",
                                  "Non puoi modificare una prenotazione per una data già trascorsa.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("validation passed, attempting to update booking ID {} for the period: {} - {}", bookingId, formatTimestamp(startTime), formatTimestamp(endTime));

        Booking booking;
        try {
            booking = bookingService.updateBooking(
                bookingId, request.getRoomId(), request.getCourseId(), principal.id(), principal.isAdmin(), startTime, endTime, request.getDescription());
        } catch (DataIntegrityViolationException e) {
            logger.warn("END updateBooking - conflict raised by the database constraint (concurrent booking) - bookingId: {}, roomId: {}", bookingId, request.getRoomId());
            throw new BookingConflictException("UPDATE_CONFLICT", "Impossibile modificare la prenotazione",
                    "L'aula è appena stata prenotata da un'altra richiesta per il nuovo periodo. Riprova con un altro orario.");
        }

        logger.debug("END updateBooking - booking updated - ID: {}, roomId: {}, userId: {}", booking.getId(), request.getRoomId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione modificata con successo",
                                new BookingAckPayload(booking, request.getRoomId(), formatTimestamp(startTime) + " - " + formatTimestamp(endTime)),
                                sessionId),
            HttpStatus.OK
        );
    }

    // Blocks a room. Admin only.
    @PostMapping("/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Block a room for a period (admin only)")
    public ResponseEntity<ApiEnvelope<BlockAckPayload>> blockRoom(@Valid @RequestBody BookingRequest request,
                                       @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("START bloccaroom - roomId: {}, period: {} - {}", request.getRoomId(), request.getStartTime(), request.getEndTime());

        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.parse(request.getStartTime());
        } catch (DateTimeParseException e) {
            logger.warn("END bloccaroom - could not parse the start date: '{}'", request.getStartTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            endTime = LocalDateTime.parse(request.getEndTime());
        } catch (DateTimeParseException e) {
            logger.warn("END bloccaroom - could not parse the end date: '{}'", request.getEndTime());
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (endTime.isBefore(startTime)) {
            logger.warn("END bloccaroom - the end date is before the start date");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("validation passed, attempting to block the room for the period: {} - {}", formatTimestamp(startTime), formatTimestamp(endTime));

        Booking blocco;
        try {
            blocco = bookingService.blockRoom(request.getRoomId(), snapshotOf(principal), startTime, endTime, request.getDescription());
        } catch (DataIntegrityViolationException e) {
            logger.warn("END bloccaroom - conflict raised by the database constraint (concurrent booking) - roomId: {}", request.getRoomId());
            throw new BookingConflictException("BLOCK_CONFLICT", "Impossibile bloccare l'aula",
                    "L'aula è appena stata occupata da un'altra richiesta per lo stesso periodo.");
        }

        logger.debug("END bloccaroom - room blocked - block ID: {}, roomId: {}, Admin: {}", blocco.getId(), request.getRoomId(), principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Aula bloccata con successo",
                                new BlockAckPayload(blocco, request.getRoomId(), formatTimestamp(startTime) + " - " + formatTimestamp(endTime), principal.id()),
                                sessionId),
            HttpStatus.CREATED
        );
    }

    // Is the room free over the period?
    @GetMapping("/availability")
    @Operation(summary = "Check whether a room is free over a period")
    public ResponseEntity<ApiEnvelope<AvailabilityPayload>> checkAvailability(@RequestParam("roomId") Long roomId,
                                                   @RequestParam("start") String startTime,
                                                   @RequestParam("end") String endTime) {
        String sessionId = generateSessionId();
        logger.debug("START checkAvailability - roomId: {}, period: {} - {}", roomId, startTime, endTime);

        LocalDateTime startDateTime;
        LocalDateTime endDateTime;
        try {
            startDateTime = LocalDateTime.parse(startTime);
        } catch (DateTimeParseException e) {
            logger.warn("END checkAvailability - could not parse the start date: '{}'", startTime);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_START_DATE", "Invalid start date format",
                                  "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            endDateTime = LocalDateTime.parse(endTime);
        } catch (DateTimeParseException e) {
            logger.warn("END checkAvailability - could not parse the end date: '{}'", endTime);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_END_DATE", "Invalid end date format",
                                  "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        if (endDateTime.isBefore(startDateTime)) {
            logger.warn("END checkAvailability - the end date is before the start date");
            return new ResponseEntity<>(
                createErrorResponse("INVALID_DATE_RANGE", "Invalid time range",
                                  "La data di fine deve essere successiva alla data di inizio.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("availability check for roomId: {} over the period: {} - {}", roomId, formatTimestamp(startDateTime), formatTimestamp(endDateTime));
        boolean available = bookingService.isRoomAvailable(roomId, startDateTime, endDateTime);
        logger.debug("END checkAvailability - roomId: {}, available: {}", roomId, available);

        return new ResponseEntity<>(
            createSuccessResponse("Verifica disponibilità completata",
                                new AvailabilityPayload(roomId, available, formatTimestamp(startDateTime) + " - " + formatTimestamp(endDateTime)),
                                sessionId),
            HttpStatus.OK
        );
    }

    // The current status of a room.
    // The path is "/room-status/{roomId}" and not "/status/{roomId}": the latter collided
    // with "/status/{status}" (bookings by status) further down. Being the same path
    // pattern, Spring registered both but failed at runtime with "Ambiguous handler methods
    // mapped", so BOTH endpoints answered 500. No working client could have depended on the
    // old path, which is why the rename was safe.
    @GetMapping("/room-status/{roomId}")
    @Operation(summary = "The current status of a room")
    public ResponseEntity<RoomStatusPayload> getRoomStatus(@PathVariable("roomId") Long roomId) {
        logger.debug("START getstatusroom - roomId: {}", roomId);
        String status = bookingService.getRoomStatus(roomId, LocalDateTime.now());
        logger.debug("END getstatusroom - roomId: {}, status: {}", roomId, status);
        return ResponseEntity.ok(new RoomStatusPayload(roomId, status, LocalDateTime.now()));
    }

    // The user's bookings - cancelled ones are excluded automatically.
    @GetMapping("/mine")
    @Operation(summary = "The bookings of the authenticated user")
    public ResponseEntity<SingleBookingPayload> getMyBookings(@AuthenticationPrincipal AppPrincipal principal) {
        logger.debug("START getMyBookings");
        List<Booking> allBookings = bookingService.getUserBookings(principal.id());

        List<Booking> bookings = allBookings.stream()
            .filter(p -> p.getStatus() != BookingStatus.CANCELLED)
            .collect(Collectors.toList());

        logger.debug("END getMyBookings - active bookings fetched for user: {}, total: {} ({} cancelled excluded)",
                   principal.id(), bookings.size(), allBookings.size() - bookings.size());
        return ResponseEntity.ok(new SingleBookingPayload(bookings));
    }

    // Annulla prenotazione
    @DeleteMapping("/{bookingId}")
    @Operation(summary = "Cancel a booking (owner or admin only)")
    public ResponseEntity<ApiEnvelope<CancellationAckPayload>> cancelBooking(@PathVariable("bookingId") Long bookingId,
                                                @AuthenticationPrincipal AppPrincipal principal) {
        String sessionId = generateSessionId();
        logger.debug("START cancelBooking - bookingId: {}", bookingId);


        // No check on the outcome, and above all no reconstruction of the reason: the
        // service already throws AccessDeniedException for the wrong owner,
        // DomainConflictException for a status that cannot be cancelled, and
        // ResourceNotFoundException when it does not exist. This block used to REDO those
        // checks in order to interpret a boolean, and a comment warned you to keep their
        // order in step with the service's: two copies of the same rule to synchronise by
        // hand.
        bookingService.cancelBooking(bookingId, principal.id(), principal.isAdmin());


        logger.debug("END cancelBooking - booking cancelled | bookingId: {} | userId: {}", bookingId, principal.id());
        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione annullata con successo",
                                new CancellationAckPayload(bookingId, principal.id(), formatTimestamp(LocalDateTime.now())),
                                sessionId),
            HttpStatus.OK
        );
    }

    // Every booking, in the simple shape - OPEN TO ANY AUTHENTICATED USER.
    // Cancelled bookings are excluded automatically.
    @GetMapping
    @Operation(summary = "List every active booking (the owner's personal data is stripped)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SingleBookingPayload.class)))
    public ResponseEntity<?> getAllBookings() {
        logger.debug("START getAllBookings");

        List<Booking> allBookings = bookingService.getAllBookings();
        List<Booking> bookings = allBookings.stream()
            .filter(p -> p.getStatus() != BookingStatus.CANCELLED)
            .map(this::sanitizeOwnerForListing)
            .collect(Collectors.toList());

        if (bookings.isEmpty()) {
            logger.debug("END getAllBookings - no active booking found");
            return ResponseEntity.ok(new MessageResponse("Nessuna prenotazione attiva trovata"));
        }

        logger.debug("END getAllBookings - active bookings fetched: {} (total including cancelled: {})",
                   bookings.size(), allBookings.size());
        return ResponseEntity.ok(new SingleBookingPayload(bookings));
    }

    // A single booking by id, in the simple shape - OWNER OR ADMIN ONLY.
    @GetMapping("/{id}")
    @PreAuthorize("@prenotazioneAuth.isOwnerOrAdmin(#id, principal)")
    @Operation(summary = "Fetch a single booking (owner or admin only)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = BookingWrapper.class)))
    public ResponseEntity<?> getBookingById(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("START getBookingById - booking ID: {}", id);

        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            // It used to be {"error":"Prenotazione non trovata"} - no "success", no
            // "userMessage", and "error" held a sentence instead of a code. A client reading
            // userMessage got undefined on exactly these two endpoints.
            throw ResourceNotFoundException.forId(ResourceType.BOOKING, id);
        }

        logger.debug("END getBookingById - booking fetched: ID: {}", booking.getId());
        return ResponseEntity.ok(new BookingWrapper(booking));
    }

    // Full details of one particular booking - OWNER OR ADMIN ONLY.
    @GetMapping("/{id}/details")
    @PreAuthorize("@prenotazioneAuth.isOwnerOrAdmin(#id, principal)")
    @Operation(summary = "Full details of one booking (owner or admin only)")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = BookingWithDetailsPayload.class)))
    public ResponseEntity<?> getBookingDetailsById(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("START getBookingDetailsById - booking ID: {}", id);

        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            // It used to be {"error":"Prenotazione non trovata"} - no "success", no
            // "userMessage", and "error" held a sentence instead of a code. A client reading
            // userMessage got undefined on exactly these two endpoints.
            throw ResourceNotFoundException.forId(ResourceType.BOOKING, id);
        }

        logger.debug("booking found: ID: {}", booking.getId());
        List<BookingDetailDto> fullDetails = bookingService.getBookingCompleteDetails(id);
        logger.debug("END getBookingDetailsById - full details fetched, total details: {}", fullDetails.size());
        return ResponseEntity.ok(new BookingWithDetailsPayload(booking, fullDetails));
    }

    // The full view of every booking with its details - OPEN TO ANY AUTHENTICATED USER.
    @GetMapping("/all-details")
    @Operation(summary = "Full details of every booking")
    public ResponseEntity<BookingDetailListPayload> getAllBookingsWithDetails() {
        logger.debug("START getAllBookingsWithDetails");
        List<BookingDetailDto> fullDetails = bookingService.getAllCompleteDetails();
        logger.debug("END getAllBookingsWithDetails - full details fetched, total bookings: {}", fullDetails.size());
        return ResponseEntity.ok(new BookingDetailListPayload(fullDetails));
    }

    // Bookings by status - OPEN TO ANY AUTHENTICATED USER.
    @GetMapping("/status/{status}")
    @Operation(summary = "List the bookings in a given status")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = BookingsByStatusPayload.class)))
    public ResponseEntity<?> getBookingsByStatus(@PathVariable("status") String status) {
        logger.debug("START getPrenotazioniBystatus - status: {}", status);
        try {
            List<Booking> bookings = bookingService.getBookingsByStatus(status.toLowerCase())
                .stream().map(this::sanitizeOwnerForListing).collect(Collectors.toList());

            logger.debug("END getPrenotazioniBystatus - bookings fetched for status: {}, total: {}", status, bookings.size());
            return ResponseEntity.ok(new BookingsByStatusPayload(status, bookings));
        } catch (IllegalArgumentException e) {
            logger.debug("END getPrenotazioniBystatus - invalid status: {}", status);
            // A status that does not exist is invalid input, so a 400 in the common
            // envelope. The list of allowed statuses is derived from the enum instead of
            // being written by hand: a hand-written one would have drifted at the first
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
    @Operation(summary = "List the bookings that start in the future")
    public ResponseEntity<BookingsListWithTotalPayload> getFutureBookings() {
        logger.debug("START getFutureBookings");
        List<Booking> bookings = bookingService.getFutureBookings()
            .stream().map(this::sanitizeOwnerForListing).collect(Collectors.toList());
        logger.debug("END getFutureBookings - future bookings fetched, total: {}", bookings.size());
        return ResponseEntity.ok(new BookingsListWithTotalPayload(bookings));
    }
}
