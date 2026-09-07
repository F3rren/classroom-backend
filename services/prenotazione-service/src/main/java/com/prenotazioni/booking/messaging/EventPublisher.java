package com.prenotazioni.booking.messaging;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.events.BookingCancelledEvent;
import com.prenotazioni.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes to the broker the events other services may want to know about.
 *
 * The cancellation notification used to be a direct REST call to notification-service. The
 * problem was not the latency but the loss: if that service was down, the notification
 * vanished - logged and swallowed, because an error there certainly must not undo a
 * cancellation that has already happened.
 *
 * With a queue the dependency moves from the service to the broker, and that is a concrete
 * gain rather than decoration: notification-service can be down, restarting or mid-upgrade
 * without a single notification being lost, because the message waits for it on the queue.
 *
 * What it does NOT solve, and this is worth saying: if the broker is unreachable the
 * publish fails and the message is lost all the same. The window narrowed, it did not close.
 * That is why the failure stays logged as an error and is not propagated: the booking has
 * already been
 * cancelled, and failing the admin's response would not bring it back.
 */
@Component
public class EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * @return true when the message reached the broker. The caller must not make the outcome
     *         of the cancellation depend on this: it only decides what to write in the log.
     */
    public boolean publishCancellation(BookingCancelledEvent event) {
        try {
            // The id travels as a message header and not as a field of the record: the
            // payload is a contract between two services, and must not be widened for a
            // value that only helps read the logs. This way the notification the consumer
            // creates later ties back to the cancellation that caused it.
            String requestId = RequestCorrelationFilter.current();
            rabbitTemplate.convertAndSend(
                    EventTopology.EXCHANGE,
                    EventTopology.ROUTING_KEY_CANCELLATION,
                    event,
                    message -> {
                        message.getMessageProperties()
                                .setHeader(RequestCorrelationFilter.HEADER, requestId);
                        return message;
                    });
            logger.debug("cancellation event published for userId={}, bookingId={}",
                    event.userId(), event.bookingId());
            return true;
        } catch (Exception e) {
            logger.error("cancellation event NOT published for userId={}, bookingId={}: "
                            + "la notifica andra' persa. Causa: {}",
                    event.userId(), event.bookingId(), e.getMessage());
            return false;
        }
    }
}
