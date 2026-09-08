package com.classroom.booking.controller.admin;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.booking.service.RoomService;
import com.classroom.booking.service.BookingService;
import com.classroom.events.BookingCancelledEvent;
import com.classroom.booking.messaging.EventPublisher;
import com.classroom.dto.*;
// both: com.classroom.dto keeps the classes shared holds in common,
// com.classroom.booking.dto the ones belonging to this service
import com.classroom.booking.dto.*;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingOwner;
import com.classroom.booking.model.BookingStatus;
import com.classroom.security.AppPrincipal;
import java.util.List;
import java.util.Optional;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Every endpoint here requires the admin role: applied once at class level instead of the
 * manual checkAdminAccess() that used to be repeated in every method.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final RoomService roomService;
    private final BookingService bookingService;
    private final EventPublisher eventPublisher;

    AdminController(RoomService roomService, BookingService bookingService,
                     EventPublisher eventPublisher) {
        this.roomService = roomService;
        this.bookingService = bookingService;
        this.eventPublisher = eventPublisher;
    }

    /** The same id the error handler will see, not a different one. */
    private String generateSessionId() {
        return RequestCorrelationFilter.current();
    }

    private <T> ApiEnvelope<T> createErrorResponse(String errorCode, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(errorCode, message, userMessage, sessionId);
    }

    private <T> ApiEnvelope<T> createSuccessResponse(String message, T data, String sessionId) {
        return ApiEnvelope.success(message, data, sessionId);
    }

    // ==================== USER MANAGEMENT ENDPOINTS ====================


    @GetMapping("/rooms")
    @Operation(summary = "List every room (admin only)")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getAllRooms() {
        String sessionId = generateSessionId();
        logger.debug("START getAllRooms (admin) - full room list requested");

        List<Room> rooms = roomService.getAllRooms();
        logger.debug("END getAllRooms - rooms fetched, total: {}", rooms.size());
        return new ResponseEntity<>(
            createSuccessResponse(rooms.isEmpty() ? "Nessuna aula presente nel sistema" : "Lista aule recuperata con successo",
                                RoomListPayload.of(rooms), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/rooms/{id}")
    @Operation(summary = "Fetch a single room by id (admin only)")
    public ResponseEntity<ApiEnvelope<RoomWrapper<Room>>> getRoomById(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("START getRoomById (admin) - ID room: {}", id);

        if (id == null || id <= 0) {
            logger.warn("END getRoomById - invalid room ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Optional<Room> room = roomService.getRoomById(id);
        if (room.isEmpty()) {
            logger.warn("END getRoomById - no room found with ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("ROOM_NOT_FOUND", "Room not found",
                                  String.format("L'aula con ID %d non esiste.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("END getRoomById - room fetched: ID: {}, name: {}", room.get().getId(), room.get().getName());
        return new ResponseEntity<>(
            createSuccessResponse("Aula recuperata con successo", new RoomWrapper<>(room.get()), sessionId),
            HttpStatus.OK
        );
    }

    @PostMapping("/rooms")
    @Operation(summary = "Create a room (admin only)")
    public ResponseEntity<ApiEnvelope<RoomAckPayload>> createRoom(@Valid @RequestBody RoomRequest roomRequest) {
        String sessionId = generateSessionId();
        logger.debug("START createRoom | name: {} | floor: {} | capacity: {}", roomRequest.getName(), roomRequest.getFloor(), roomRequest.getCapacity());

        Room newRoom = roomService.createRoom(roomRequest);
        logger.debug("END createRoom - room created | ID: {} | name: {}", newRoom.getId(), newRoom.getName());
        return new ResponseEntity<>(
            createSuccessResponse("Aula creata con successo", new RoomAckPayload(newRoom), sessionId),
            HttpStatus.CREATED
        );
    }

    @PutMapping("/rooms/{id}")
    @Operation(summary = "Update an existing room (admin only)")
    public ResponseEntity<ApiEnvelope<RoomAckPayload>> updateRoom(@PathVariable("id") Long id, @Valid @RequestBody RoomRequest roomRequest) {
        String sessionId = generateSessionId();
        logger.debug("START updateRoom | ID room: {} | new name: {} | floor: {} | capacity: {}", id, roomRequest.getName(), roomRequest.getFloor(), roomRequest.getCapacity());

        if (id == null || id <= 0) {
            logger.warn("END updateRoom - invalid room ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Room updatedRoom = roomService.updateRoom(id, roomRequest);
        logger.debug("END updateRoom - room updated | ID: {} | name: {}", updatedRoom.getId(), updatedRoom.getName());
        return new ResponseEntity<>(
            createSuccessResponse("Aula aggiornata con successo", new RoomAckPayload(updatedRoom), sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/rooms/{id}")
    @Operation(summary = "Delete a room (admin only)")
    public ResponseEntity<ApiEnvelope<DeletedRoomResponse>> deleteRoom(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("START deleteRoom - ID room: {}", id);

        if (id == null || id <= 0) {
            logger.warn("END deleteRoom - invalid room ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        // No check on the outcome: deleteRoom throws ResourceNotFoundException when the
        // room is not there, and the global handler turns that into a 404. The boolean used
        // to confuse "does not exist" with "could not be deleted", and the message admitted
        // as much: "does not exist OR cannot be deleted".
        roomService.deleteRoom(id);

        logger.debug("END deleteRoom - room deleted - ID: {}", id);
        return new ResponseEntity<>(
            createSuccessResponse("Aula eliminata con successo", new DeletedRoomResponse(id), sessionId),
            HttpStatus.OK
        );
    }

    // ========== admin booking management ==========

    @GetMapping("/bookings")
    @Operation(summary = "List every booking, cancelled ones included (admin only)")
    public ResponseEntity<ApiEnvelope<AdminBookingsPayload>> getAllBookingsForAdmin() {
        String sessionId = generateSessionId();
        logger.debug("START getAllBookingsForAdmin");

        List<Booking> allBookings = bookingService.getAllBookings();
        long active = allBookings.stream()
            .filter(p -> p.getStatus() != BookingStatus.CANCELLED)
            .count();
        long cancelled = allBookings.size() - active;

        logger.debug("END getAllBookingsForAdmin - total: {} (active: {}, cancelled: {})", allBookings.size(), active, cancelled);

        AdminBookingsPayload payload = new AdminBookingsPayload(
            allBookings, new BookingStats(allBookings.size(), active, cancelled));

        return new ResponseEntity<>(
            createSuccessResponse("Prenotazioni recuperate con successo", payload, sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/bookings/{id}")
    @Operation(summary = "Force-delete any booking at all (admin only)")
    public ResponseEntity<ApiEnvelope<BookingDeletionResponse>> deleteBookingAsAdmin(@PathVariable("id") Long id,
                                                      @AuthenticationPrincipal AppPrincipal principal,
                                                      @Valid @RequestBody(required = false) DeleteReasonRequest requestBody) {
        String sessionId = generateSessionId();
        logger.debug("START deleteBookingAsAdmin - booking ID: {}", id);

        if (id == null || id <= 0) {
            logger.warn("END deleteBookingAsAdmin - invalid booking ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_BOOKING_ID", "Invalid prenotazione id",
                                  "L'ID della prenotazione deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Long adminId = principal.id();
        logger.info("Admin ID: {} is trying to delete booking: {}", adminId, id);

        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            logger.warn("END deleteBookingAsAdmin - booking not found, ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("BOOKING_NOT_FOUND", "Prenotazione not found",
                                  String.format("La prenotazione con ID %d non esiste.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        BookingOwner bookingUser = booking.getUser();
        Room bookingRoom = booking.getRoom();

        String reason = (requestBody != null && requestBody.getReason() != null)
            ? requestBody.getReason()
            : "Eliminazione da parte dell'amministratore";
        logger.debug("deletion reason: {}", reason);

        boolean deleted = bookingService.cancelBookingAsAdmin(id, adminId, reason);
        if (!deleted) {
            logger.warn("END deleteBookingAsAdmin - could not delete booking ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("BOOKING_DELETION_FAILED", "Could not delete the prenotazione",
                                  String.format("La prenotazione con ID %d non può essere eliminata.", id), sessionId),
                HttpStatus.CONFLICT
            );
        }

        try {
            // The admin's name comes from the token: asking auth-service for it would mean
            // a network call just to compose the text of a notification.
            //
            // Neither field is defaulted here when missing: what to show the recipient when
            // the room is gone or the admin's name is unavailable is notification-service's
            // call, since it is the one that owns the wording shown to a person. Passing
            // null and letting it decide keeps that decision in one place instead of two.
            String adminName = principal.name();
            String bookingDate = booking.getStartTime().toLocalDate().toString();
            String startTime = booking.getStartTime().toLocalTime().toString();
            String endTime = booking.getEndTime().toLocalTime().toString();
            String roomName = bookingRoom != null ? bookingRoom.getName() : null;

            // Published to a queue rather than called over REST: that way the notification
            // is not lost if notification-service is down. The typed record also replaced
            // the map of strings that used to be here, where a wrong field name would have
            // arrived as a plain missing value.
            eventPublisher.publishCancellation(new BookingCancelledEvent(
                    bookingUser.getId(), id, roomName, adminName,
                    bookingDate, startTime, endTime, reason));

            logger.debug("cancellation notification created for user: {}", bookingUser.getId());
        } catch (Exception e) {
            logger.error("failure while creating the notification for user: {} | error: {}", bookingUser.getId(), e.getMessage(), e);
            // A failed notification does not hold up the operation
        }

        logger.debug("END deleteBookingAsAdmin - booking deleted | ID: {} | Admin: {} | reason: {}", id, adminId, reason);

        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione eliminata con successo dall'amministratore",
                                new BookingDeletionResponse(id, adminId, reason), sessionId),
            HttpStatus.OK
        );
    }
}
