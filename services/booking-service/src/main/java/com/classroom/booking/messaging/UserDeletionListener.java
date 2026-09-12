package com.classroom.booking.messaging;

import com.classroom.booking.service.BookingService;
import com.classroom.config.RequestCorrelationFilter;
import com.classroom.events.EventTopology;
import com.classroom.events.UserDeletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Turns a user-deletion event from auth-service into the removal of that user's bookings.
 *
 * This used to be InternalBookingController's one endpoint, called synchronously by
 * auth-service and requiring an ADMIN token forwarded across the network. Now booking-service
 * finds out on its own, from the same broker that already carries BookingCancelledEvent the
 * other way.
 *
 * ON EXCEPTIONS: same split as CancellationListener. An event with no userId is discarded and
 * logged, because retrying will never fix it; a database failure is propagated, because that
 * one really can succeed next time.
 */
@Slf4j
@Component
public class UserDeletionListener {

    private final BookingService bookingService;

    UserDeletionListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @RabbitListener(queues = EventTopology.USER_DELETED_BOOKINGS_QUEUE)
    public void onUserDeleted(
            UserDeletedEvent event,
            @Header(name = RequestCorrelationFilter.HEADER, required = false) String requestId) {
        // required = false because a message published before this listener existed, or by
        // another version, has to keep being consumed.
        RequestCorrelationFilter.applyToMdc(requestId);
        try {
            if (event == null || event.userId() == null) {
                // Discarded on purpose: with no id there is nothing to delete, and
                // requeueing it would make it spin forever.
                logger.error("User-deletion event discarded: it carries no userId: {}", event);
                return;
            }

            logger.debug("User-deletion event received for userId={}", event.userId());
            bookingService.deleteBookingsOfUser(event.userId());
            logger.info("Bookings deleted from event for userId={}", event.userId());
        } finally {
            RequestCorrelationFilter.clearMdc();
        }
    }
}
