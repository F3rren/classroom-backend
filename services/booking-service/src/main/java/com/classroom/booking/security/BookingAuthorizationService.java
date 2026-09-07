package com.classroom.booking.security;

import com.classroom.booking.model.Booking;
import com.classroom.security.AppPrincipal;
import com.classroom.booking.service.BookingService;
import org.springframework.stereotype.Component;

/**
 * Usato da @PreAuthorize("@prenotazioneAuth.isOwnerOrAdmin(#id, principal)") sugli endpoint
 * read endpoints of BookingController, in place of the imperative isOwnerOrAdmin check that
 * used to live in the controller.
 */
@Component("prenotazioneAuth")
public class BookingAuthorizationService {

    private final BookingService bookingService;

    public BookingAuthorizationService(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public boolean isOwnerOrAdmin(Long bookingId, AppPrincipal principal) {
        // false here is not a disguised error: this method IS a predicate, and answering
        // "no" to "may you act?" is exactly its job.
        if (principal == null) {
            return false;
        }
        Booking booking = bookingService.getBookingById(bookingId);
        // If the booking does not exist we do not block here: we let the controller answer
        // 404 (the behaviour that was already there) rather than masking it with a 403.
        if (booking == null) {
            return true;
        }
        return booking.getUser().getId().equals(principal.id()) || principal.isAdmin();
    }
}
