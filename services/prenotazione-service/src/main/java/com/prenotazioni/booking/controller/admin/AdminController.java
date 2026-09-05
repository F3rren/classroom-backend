package com.prenotazioni.booking.controller.admin;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.booking.service.RoomService;
import com.prenotazioni.booking.service.BookingService;
import com.prenotazioni.events.BookingCancelledEvent;
import com.prenotazioni.booking.messaging.EventPublisher;
import com.prenotazioni.dto.*;
// entrambe: in com.prenotazioni.dto restano le classi comuni di shared,
// in com.prenotazioni.booking.dto quelle di questo servizio
import com.prenotazioni.booking.dto.*;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingOwner;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.security.AppPrincipal;
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
 * Tutti gli endpoint richiedono ruolo admin: applicato una sola volta a livello di classe
 * invece del controllo manuale checkAdminAccess() ripetuto in ogni metodo.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Amministrazione")
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

    /** Lo stesso identificativo che vedra' il gestore degli errori, non uno diverso. */
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
    @Operation(summary = "Elenca tutte le aule (solo admin)")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getAllRooms() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getAllRooms (admin) - Richiesta lista completa aule");

        List<Room> rooms = roomService.getAllRooms();
        logger.debug("FINE getAllRooms - Aule recuperate con successo, totale: {}", rooms.size());
        return new ResponseEntity<>(
            createSuccessResponse(rooms.isEmpty() ? "Nessuna aula presente nel sistema" : "Lista aule recuperata con successo",
                                RoomListPayload.of(rooms), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/rooms/{id}")
    @Operation(summary = "Recupera una singola aula per ID (solo admin)")
    public ResponseEntity<ApiEnvelope<RoomWrapper<Room>>> getRoomById(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getRoomById (admin) - ID Aula: {}", id);

        if (id == null || id <= 0) {
            logger.warn("FINE getRoomById - ID aula non valido: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Optional<Room> room = roomService.getRoomById(id);
        if (room.isEmpty()) {
            logger.warn("FINE getRoomById - Aula non trovata con ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("ROOM_NOT_FOUND", "Aula not found",
                                  String.format("L'aula con ID %d non esiste.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("FINE getRoomById - Aula recuperata con successo: ID: {}, Nome: {}", room.get().getId(), room.get().getName());
        return new ResponseEntity<>(
            createSuccessResponse("Aula recuperata con successo", new RoomWrapper<>(room.get()), sessionId),
            HttpStatus.OK
        );
    }

    @PostMapping("/rooms")
    @Operation(summary = "Crea una nuova aula (solo admin)")
    public ResponseEntity<ApiEnvelope<RoomAckPayload>> createRoom(@Valid @RequestBody RoomRequest roomRequest) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO createRoom | Nome: {} | Piano: {} | Capienza: {}", roomRequest.getName(), roomRequest.getFloor(), roomRequest.getCapacity());

        Room newRoom = roomService.createRoom(roomRequest);
        logger.debug("FINE createRoom - Aula creata con successo | ID: {} | Nome: {}", newRoom.getId(), newRoom.getName());
        return new ResponseEntity<>(
            createSuccessResponse("Aula creata con successo", new RoomAckPayload(newRoom), sessionId),
            HttpStatus.CREATED
        );
    }

    @PutMapping("/rooms/{id}")
    @Operation(summary = "Modifica un'aula esistente (solo admin)")
    public ResponseEntity<ApiEnvelope<RoomAckPayload>> updateRoom(@PathVariable("id") Long id, @Valid @RequestBody RoomRequest roomRequest) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO updateRoom | ID Aula: {} | Nuovo Nome: {} | Piano: {} | Capienza: {}", id, roomRequest.getName(), roomRequest.getFloor(), roomRequest.getCapacity());

        if (id == null || id <= 0) {
            logger.warn("FINE updateRoom - ID aula non valido: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Room updatedRoom = roomService.updateRoom(id, roomRequest);
        logger.debug("FINE updateRoom - Aula aggiornata con successo | ID: {} | Nome: {}", updatedRoom.getId(), updatedRoom.getName());
        return new ResponseEntity<>(
            createSuccessResponse("Aula aggiornata con successo", new RoomAckPayload(updatedRoom), sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/rooms/{id}")
    @Operation(summary = "Elimina un'aula (solo admin)")
    public ResponseEntity<ApiEnvelope<DeletedRoomResponse>> deleteRoom(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO deleteRoom - ID Aula: {}", id);

        if (id == null || id <= 0) {
            logger.warn("FINE deleteRoom - ID aula non valido: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        // Nessun controllo sull'esito: deleteAula lancia ResourceNotFoundException se
        // l'aula non c'e', e il gestore globale la traduce in 404. Prima il booleano
        // confondeva "non esiste" con "non si e' potuta eliminare", e il messaggio lo
        // ammetteva: "non esiste O non puo' essere eliminata".
        roomService.deleteRoom(id);

        logger.debug("FINE deleteRoom - Aula eliminata con successo - ID: {}", id);
        return new ResponseEntity<>(
            createSuccessResponse("Aula eliminata con successo", new DeletedRoomResponse(id), sessionId),
            HttpStatus.OK
        );
    }

    // ========== GESTIONE PRENOTAZIONI ADMIN ==========

    @GetMapping("/bookings")
    @Operation(summary = "Elenca tutte le prenotazioni, incluse annullate (solo admin)")
    public ResponseEntity<ApiEnvelope<AdminBookingsPayload>> getAllBookingsForAdmin() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getAllPrenotazioniAdmin");

        List<Booking> allBookings = bookingService.getAllBookings();
        long attive = allBookings.stream()
            .filter(p -> p.getStatus() != BookingStatus.CANCELLED)
            .count();
        long annullate = allBookings.size() - attive;

        logger.debug("FINE getAllPrenotazioniAdmin - Totale: {} (Attive: {}, Annullate: {})", allBookings.size(), attive, annullate);

        AdminBookingsPayload payload = new AdminBookingsPayload(
            allBookings, new BookingStats(allBookings.size(), attive, annullate));

        return new ResponseEntity<>(
            createSuccessResponse("Prenotazioni recuperate con successo", payload, sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/bookings/{id}")
    @Operation(summary = "Elimina forzatamente qualsiasi prenotazione (solo admin)")
    public ResponseEntity<ApiEnvelope<BookingDeletionResponse>> deleteBookingAsAdmin(@PathVariable("id") Long id,
                                                      @AuthenticationPrincipal AppPrincipal principal,
                                                      @Valid @RequestBody(required = false) DeleteReasonRequest requestBody) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO deletePrenotazioneAsAdmin - ID Prenotazione: {}", id);

        if (id == null || id <= 0) {
            logger.warn("FINE deletePrenotazioneAsAdmin - ID prenotazione non valido: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_BOOKING_ID", "Invalid prenotazione id",
                                  "L'ID della prenotazione deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Long adminId = principal.id();
        logger.info("Admin ID: {} tenta di eliminare prenotazione: {}", adminId, id);

        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            logger.warn("FINE deletePrenotazioneAsAdmin - Prenotazione non trovata ID: {}", id);
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
        logger.debug("Motivo eliminazione: {}", reason);

        boolean deleted = bookingService.cancelBookingAsAdmin(id, adminId, reason);
        if (!deleted) {
            logger.warn("FINE deletePrenotazioneAsAdmin - Impossibile eliminare prenotazione ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("BOOKING_DELETION_FAILED", "Could not delete the prenotazione",
                                  String.format("La prenotazione con ID %d non può essere eliminata.", id), sessionId),
                HttpStatus.CONFLICT
            );
        }

        try {
            // Il nome dell'admin arriva dal token: chiederlo ad auth-service significherebbe
            // una chiamata di rete per compilare il testo di una notifica.
            String adminName = principal.name() != null ? principal.name() : "Amministratore";
            String bookingDate = booking.getStartTime().toLocalDate().toString();
            String startTime = booking.getStartTime().toLocalTime().toString();
            String endTime = booking.getEndTime().toLocalTime().toString();
            String roomName = bookingRoom != null ? bookingRoom.getName() : "Stanza non specificata";

            // Pubblicato su coda e non chiamato via REST: cosi' la notifica non si perde
            // se notifica-service e' spento. Il record tipizzato ha anche sostituito la
            // mappa di stringhe che c'era prima, dove un nome di campo sbagliato sarebbe
            // arrivato a destinazione come semplice valore mancante.
            eventPublisher.publishCancellation(new BookingCancelledEvent(
                    bookingUser.getId(), id, roomName, adminName,
                    bookingDate, startTime, endTime, reason));

            logger.debug("Notifica di cancellazione creata per utente: {}", bookingUser.getId());
        } catch (Exception e) {
            logger.error("Errore durante creazione notifica per utente: {} | Errore: {}", bookingUser.getId(), e.getMessage(), e);
            // Non blocchiamo l'operazione se la notifica fallisce
        }

        logger.debug("FINE deletePrenotazioneAsAdmin - Prenotazione eliminata con successo | ID: {} | Admin: {} | Motivo: {}", id, adminId, reason);

        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione eliminata con successo dall'amministratore",
                                new BookingDeletionResponse(id, adminId, reason), sessionId),
            HttpStatus.OK
        );
    }
}
