package com.classroom.auth.messaging;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.events.EventTopology;
import com.classroom.events.UserDeletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the events this service raises to the broker.
 *
 * Replaces UserDataClient: deleting a user used to mean calling booking-service and
 * notification-service over REST, synchronously, and waiting for both to answer before the
 * user itself could be removed. Now the user is removed first and this only announces it -
 * exactly the same shift booking-service already made for the cancellation notification.
 *
 * What it does NOT solve, and this is worth saying: if the broker is unreachable the publish
 * fails and the two services never learn the user is gone, so their rows are never cleaned
 * up. That is why the failure stays logged as an error and is not propagated: the user has
 * already been deleted, and failing the admin's response would not bring it back.
 */
@Slf4j
@Component
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * @return true when the message reached the broker. The caller must not make the
     *         outcome of the deletion depend on this: it only decides what to write in the
     *         log.
     */
    public boolean publishUserDeleted(UserDeletedEvent event) {
        try {
            // The id travels as a message header and not as a field of the record, for the
            // same reason as the cancellation event: the payload is a contract between
            // services and must not carry a value that only helps read the logs.
            String requestId = RequestCorrelationFilter.current();
            rabbitTemplate.convertAndSend(
                    EventTopology.EXCHANGE,
                    EventTopology.ROUTING_KEY_USER_DELETED,
                    event,
                    message -> {
                        message.getMessageProperties()
                                .setHeader(RequestCorrelationFilter.HEADER, requestId);
                        return message;
                    });
            logger.debug("user-deleted event published for userId={}", event.userId());
            return true;
        } catch (Exception e) {
            logger.error("user-deleted event NOT published for userId={}: their bookings and "
                            + "notifications will not be cleaned up. Cause: {}",
                    event.userId(), e.getMessage());
            return false;
        }
    }
}
