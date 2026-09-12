package com.classroom.notification.events;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.events.EventTopology;
import com.classroom.events.UserDeletedEvent;
import com.classroom.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Turns a user-deletion event from auth-service into the removal of that user's notifications.
 *
 * This used to be InternalNotificationController's one endpoint, called synchronously by
 * auth-service and requiring an ADMIN token forwarded across the network. Now
 * notification-service finds out on its own, from the same broker that already carries
 * BookingCancelledEvent - a second, independent listener in this service, next to
 * CancellationListener.
 *
 * ON EXCEPTIONS: same split as CancellationListener. An event with no userId is discarded and
 * logged, because retrying will never fix it; a database failure is propagated, because that
 * one really can succeed next time.
 */
@Slf4j
@Component
public class UserDeletionListener {

    private final NotificationService notificationService;

    UserDeletionListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = EventTopology.USER_DELETED_NOTIFICATIONS_QUEUE)
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
            notificationService.deleteAllByUser(event.userId());
            logger.info("Notifications deleted from event for userId={}", event.userId());
        } finally {
            RequestCorrelationFilter.clearMdc();
        }
    }
}
