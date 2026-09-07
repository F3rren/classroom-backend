package com.prenotazioni.booking.controller;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.exception.ResourceType;
import com.prenotazioni.booking.service.RoomService;
import com.prenotazioni.booking.service.BookingService;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.dto.*;
// both: com.prenotazioni.dto keeps the classes shared holds in common,
// com.prenotazioni.booking.dto the ones belonging to this service
import com.prenotazioni.booking.dto.*;

import java.util.List;
import java.util.Optional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Every endpoint here is open to any authenticated user (no role required): authentication
 * itself is already guaranteed by SecurityConfig's anyRequest().authenticated() policy, so
 * neither a manual check nor @PreAuthorize is needed.
 */
@RestController
@RequestMapping("/api/rooms")
@Tag(name = "Rooms")
public class RoomController {

    private final RoomService roomService;
    private final BookingService bookingService;

    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);

    /** A cap on the capacity filter: beyond it this is not a plausible request, it is a typo. */
    private static final int MAX_REQUESTABLE_CAPACITY = 1000;

    RoomController(RoomService roomService, BookingService bookingService) {
        this.roomService = roomService;
        this.bookingService = bookingService;
    }

    /** The same id the error handler will see, not a different one. */
    private String generateSessionId() {
        return RequestCorrelationFilter.current();
    }

    private <T> ApiEnvelope<T> createErrorResponse(String error, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(error, message, userMessage, sessionId);
    }

    private <T> ApiEnvelope<T> createSuccessResponse(String message, T data, String sessionId) {
        return ApiEnvelope.success(message, data, sessionId);
    }

    @GetMapping
    @Operation(summary = "List every room")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getAllRooms() {
        String sessionId = generateSessionId();
        logger.debug("START getAllRooms - full room list requested");

        List<Room> rooms = roomService.getAllRooms();
        logger.debug("END getAllRooms - rooms fetched, total: {}", rooms.size());
        return new ResponseEntity<>(
            createSuccessResponse(rooms.isEmpty() ? "Nessuna aula disponibile" : "Aule recuperate con successo",
                                RoomListPayload.of(rooms), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/details")
    @Operation(summary = "Full details of every booking across every room")
    public ResponseEntity<BookingDetailListPayload> getAllRoomsWithDetails() {
        logger.debug("START getAllRoomsWithDetails");
        List<BookingDetailDto> fullDetails = bookingService.getAllCompleteDetails();
        logger.debug("END getAllRoomsWithDetails - full details fetched, total bookings: {}", fullDetails.size());
        return ResponseEntity.ok(new BookingDetailListPayload(fullDetails));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single room by id")
    public ResponseEntity<ApiEnvelope<RoomDetailAckPayload>> getRoomById(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("START getRoomById - requested room ID: {}", id);

        if (id == null || id <= 0) {
            logger.warn("END getRoomById - invalid room ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Optional<Room> room = roomService.getRoomById(id);
        if (room.isEmpty()) {
            logger.warn("END getRoomById - no room found with ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("ROOM_NOT_FOUND", "Aula not found",
                                  String.format("L'aula con ID %d non esiste nel sistema.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("END getRoomById - room fetched: ID: {}, name: {}", room.get().getId(), room.get().getName());
        return new ResponseEntity<>(
            createSuccessResponse("Aula recuperata con successo", new RoomDetailAckPayload(room.get()), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/{id}/details")
    @Operation(summary = "A room together with its detailed bookings")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = RoomWithBookingsPayload.class)))
    public ResponseEntity<?> getRoomDetailsById(@PathVariable("id") Long id) {
        logger.debug("START getRoomDetailsById - ID room: {}", id);

        Optional<Room> room = roomService.getRoomById(id);
        if (room.isEmpty()) {
            // It used to be {"error":"Aula non trovata"}, a shape different from the
            // envelope used everywhere else: no "success", no "userMessage", and an "error"
            // holding a
            // frase invece di un codice.
            throw ResourceNotFoundException.forId(ResourceType.ROOM, id);
        }

        logger.debug("room found: ID: {}, name: {}", room.get().getId(), room.get().getName());
        List<BookingDetailDto> fullDetails = bookingService.getRoomCompleteDetails(id);

        logger.debug("END getRoomDetailsById - full details fetched, total bookings: {}", fullDetails.size());
        return ResponseEntity.ok(new RoomWithBookingsPayload(room.get(), fullDetails));
    }

    @GetMapping("/floor/{floor}")
    @Operation(summary = "Rooms on a given floor")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getRoomsByFloor(@PathVariable("floor") int floor) {
        String sessionId = generateSessionId();
        logger.debug("START getRoomsByFloor - floor richiesto: {}", floor);

        if (floor < 0) {
            logger.warn("END getRoomsByFloor - invalid floor: {}", floor);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_FLOOR", "Invalid floor",
                                  "Il numero del piano deve essere maggiore o uguale a 0.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        List<Room> rooms = roomService.getRoomsByFloor(floor);
        logger.debug("END getRoomsByFloor - rooms fetched for floor: {}, total: {}", floor, rooms.size());
        return new ResponseEntity<>(
            createSuccessResponse(rooms.isEmpty() ? "Nessuna aula trovata per questo piano" : "Aule recuperate con successo",
                                RoomListPayload.of(rooms).withFloor(floor), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/capacity")
    @Operation(summary = "Rooms with at least a given capacity")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getRoomsByCapacity(@RequestParam("minCapacity") int minCapacity) {
        String sessionId = generateSessionId();
        logger.debug("START getRoomsByCapacity - minimum capacity requested: {}", minCapacity);

        if (minCapacity < 0) {
            logger.warn("END getRoomsByCapacity - invalid minimum capacity: {}", minCapacity);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_CAPACITY", "Invalid capacity",
                                  "La capienza minima deve essere un numero maggiore o uguale a 0.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (minCapacity > MAX_REQUESTABLE_CAPACITY) {
            logger.warn("END getRoomsByCapacity - minimum capacity too high: {}", minCapacity);
            return new ResponseEntity<>(
                createErrorResponse("CAPACITY_TOO_HIGH", "Capacity above the allowed maximum",
                                  "La capienza minima richiesta è troppo alta. Inserisci un valore realistico (massimo "
                                  + MAX_REQUESTABLE_CAPACITY + ").", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        List<Room> rooms = roomService.getRoomsByMinCapacity(minCapacity);
        logger.debug("END getRoomsByCapacity - rooms fetched for capacity >= {}, total: {}", minCapacity, rooms.size());

        RoomListPayload payload = RoomListPayload.of(rooms).withMinCapacity(minCapacity);
        if (rooms.isEmpty()) {
            return new ResponseEntity<>(
                createSuccessResponse("Nessuna aula trovata con la capienza richiesta",
                                    payload.withSuggestion("Prova con una capienza minore"), sessionId),
                HttpStatus.OK
            );
        }
        int maxCapacityFound = rooms.stream().mapToInt(Room::getCapacity).max().orElse(0);
        return new ResponseEntity<>(
            createSuccessResponse("Aule recuperate con successo", payload.withMaxCapacityFound(maxCapacityFound), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/detailed")
    @Operation(summary = "List every room with full details")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getAllRoomsDetailed() {
        String sessionId = generateSessionId();
        logger.debug("START getAllRoomsDetailed - rooms with full details requested");

        List<RoomDetailsResponse> roomDetails = roomService.getAllRoomsWithDetails();
        logger.debug("END getAllRoomsDetailed - rooms with details fetched, total: {}", roomDetails.size());
        return new ResponseEntity<>(
            createSuccessResponse(roomDetails.isEmpty() ? "Nessuna aula con dettagli disponibile" : "Aule con dettagli recuperate con successo",
                                RoomListPayload.of(roomDetails), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/{id}/detailed")
    @Operation(summary = "Fetch a single room with full details")
    public ResponseEntity<ApiEnvelope<RoomWrapper<RoomDetailsResponse>>> getRoomDetailed(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("START getRoomDetailed - ID room: {}", id);

        if (id == null || id <= 0) {
            logger.warn("END getRoomDetailed - invalid room ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo maggiore di 0", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        RoomDetailsResponse roomDetails = roomService.getRoomWithDetails(id);
        logger.debug("END getRoomDetailed - room details fetched: ID: {}, name: {}", roomDetails.getId(), roomDetails.getName());
        return new ResponseEntity<>(
            createSuccessResponse("Dettagli aula recuperati con successo", new RoomWrapper<>(roomDetails), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/physical")
    @Operation(summary = "List the physical rooms only")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getPhysicalRooms() {
        String sessionId = generateSessionId();
        logger.debug("START getPhysicalRooms - physical rooms requested");

        List<Room> rooms = roomService.getPhysicalRoomsOrdered();
        logger.debug("END getPhysicalRooms - physical rooms fetched, total: {}", rooms.size());
        return new ResponseEntity<>(
            createSuccessResponse(rooms.isEmpty() ? "Nessuna aula fisica disponibile" : "Aule fisiche recuperate con successo",
                                RoomListPayload.of(rooms).withType("physical"), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/virtual")
    @Operation(summary = "List the virtual rooms only")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getVirtualRooms() {
        String sessionId = generateSessionId();
        logger.debug("START getVirtualRooms - virtual rooms requested");

        List<Room> rooms = roomService.getVirtualRoomsOrdered();
        logger.debug("END getVirtualRooms - virtual rooms fetched, total: {}", rooms.size());
        return new ResponseEntity<>(
            createSuccessResponse(rooms.isEmpty() ? "Nessuna aula virtuale disponibile" : "Aule virtuali recuperate con successo",
                                RoomListPayload.of(rooms).withType("virtual"), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/physical/detailed")
    @Operation(summary = "Physical rooms with full details")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getPhysicalRoomsDetailed() {
        String sessionId = generateSessionId();
        logger.debug("START getPhysicalRoomsDetailed - physical rooms with details requested");

        List<RoomDetailsResponse> roomDetails = roomService.getPhysicalRoomsWithDetails();
        logger.debug("END getPhysicalRoomsDetailed - physical rooms with details fetched, total: {}", roomDetails.size());
        return new ResponseEntity<>(
            createSuccessResponse(roomDetails.isEmpty() ? "Nessuna aula fisica con dettagli disponibile" : "Aule fisiche con dettagli recuperate con successo",
                                RoomListPayload.of(roomDetails).withType("physical"), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/virtual/detailed")
    @Operation(summary = "Virtual rooms with full details")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getVirtualRoomsDetailed() {
        String sessionId = generateSessionId();
        logger.debug("START getVirtualRoomsDetailed - virtual rooms with details requested");

        List<RoomDetailsResponse> roomDetails = roomService.getVirtualRoomsWithDetails();
        logger.debug("END getVirtualRoomsDetailed - virtual rooms with details fetched, total: {}", roomDetails.size());
        return new ResponseEntity<>(
            createSuccessResponse(roomDetails.isEmpty() ? "Nessuna aula virtuale con dettagli disponibile" : "Aule virtuali con dettagli recuperate con successo",
                                RoomListPayload.of(roomDetails).withType("virtual"), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/stats")
    @Operation(summary = "Physical versus virtual room statistics")
    public ResponseEntity<ApiEnvelope<RoomStatsPayload>> getRoomsStats() {
        String sessionId = generateSessionId();
        logger.debug("START getRoomsStats - room statistics requested");

        long physicalCount = roomService.countPhysicalRooms();
        long virtualCount = roomService.countVirtualRooms();
        RoomStats stats = new RoomStats(physicalCount, virtualCount);

        logger.debug("END getRoomsStats - statistics computed: total: {}, physical: {}, virtual: {}", stats.getTotalRooms(), physicalCount, virtualCount);

        return new ResponseEntity<>(
            createSuccessResponse("Statistiche aule recuperate con successo", new RoomStatsPayload(stats), sessionId),
            HttpStatus.OK
        );
    }
}
