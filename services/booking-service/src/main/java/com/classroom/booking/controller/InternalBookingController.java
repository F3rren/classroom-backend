package com.classroom.booking.controller;

import com.classroom.dto.MessageResponse;
import com.classroom.booking.repository.BookingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints called by other services, not by the frontend.
 *
 * It exists for one reason: when auth-service deletes a user it has to be able to remove
 * their bookings, which live in this database. While everything sat together a foreign key
 * with ON DELETE did that, and before it a single transaction. It is a network call now,
 * and it can fail.
 *
 * The gateway closes /api/bookings/internal/** off from outside: reaching it means
 * talking to this service directly. The protection does not stop there, though, because a
 * bypassed gateway must not be enough: an ADMIN token is still required, checked
 * here as on any other endpoint.
 */
@RestController
@RequestMapping("/api/bookings/internal")
@Tag(name = "Bookings (internal)", description = "Called by other services, not by the frontend")
@PreAuthorize("hasRole('ADMIN')")
public class InternalBookingController {

    private static final Logger logger = LoggerFactory.getLogger(InternalBookingController.class);

    private final BookingRepository bookingRepository;

    InternalBookingController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @DeleteMapping("/user/{userId}")
    @Operation(summary = "Delete the bookings of a user about to be removed")
    @Transactional
    public ResponseEntity<MessageResponse> deleteUserBookings(@PathVariable("userId") Long userId) {
        logger.info("deleting the bookings of userId={} at the request of the user service", userId);
        bookingRepository.deleteByUserId(userId);
        return ResponseEntity.ok(new MessageResponse("Prenotazioni dell'utente eliminate"));
    }
}
