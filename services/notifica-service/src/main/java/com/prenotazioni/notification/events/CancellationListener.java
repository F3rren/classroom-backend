package com.prenotazioni.notification.events;

import org.springframework.messaging.handler.annotation.Header;
import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.events.BookingCancelledEvent;
import com.prenotazioni.events.EventTopology;
import com.prenotazioni.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Turns a cancellation event from the booking service into a notification.
 *
 * This used to be a REST endpoint that service called directly. The practical difference is
 * that this service can now be switched off without anything being lost: the messages stay
 * on the queue and are consumed at restart.
 *
 * ON EXCEPTIONS: an error here puts the message back on the queue, and if the error is
 * permanent the message starts over forever, tying up the consumer. So two cases are kept
 * apart: a malformed message is discarded and logged, because retrying will never fix it;
 * a database write failure is propagated, because that one really can succeed next time.
 */
@Component
public class CancellationListener {

    private static final Logger logger = LoggerFactory.getLogger(CancellationListener.class);

    private final NotificationService notificationService;

    CancellationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = EventTopology.CANCELLATION_QUEUE)
    public void onCancellation(
            BookingCancelledEvent event,
            @Header(name = RequestCorrelationFilter.HEADER, required = false) String requestId) {
        // Put back into the MDC for the duration of the handling: this is what lets you read
        // in sequence the HTTP request that cancelled the booking and the notification created
        // here, which happens on another service, another thread and a moment later.
        // required = false because a message published before this change, or by another
        // version, has to keep being consumed.
        RequestCorrelationFilter.applyToMdc(requestId);
        try {
            if (event == null || event.userId() == null) {
                // Discarded on purpose: with no recipient the notification has nobody to go
                // to, and requeueing it would make it spin forever.
                logger.error("Cancellation event discarded: it carries no recipient: {}", event);
                return;
            }

            logger.debug("Cancellation event received for userId={}, bookingId={}",
                    event.userId(), event.bookingId());

            notificationService.createBookingCancelledNotification(
                    event.userId(),
                    event.bookingId(),
                    event.roomName(),
                    event.adminName(),
                    event.bookingDate(),
                    event.startTime(),
                    event.endTime(),
                    event.reason());

            logger.info("Cancellation notification created from event for userId={}", event.userId());
        } finally {
            RequestCorrelationFilter.clearMdc();
        }
    }
}
