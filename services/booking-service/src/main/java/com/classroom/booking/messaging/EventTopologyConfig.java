package com.classroom.booking.messaging;

import com.classroom.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Both sides of the topology this service takes part in: it PUBLISHES
 * BookingCancelledEvent, and it CONSUMES UserDeletedEvent.
 *
 * The exchange is declared once and shared by both directions. For the publishing side, it
 * declares only the exchange: the queue belongs to the consumer, notification-service, for
 * the same reason booking-service's own queue below belongs here and nowhere else - a
 * publisher that also declared its consumers' queues would know who is listening, and the
 * coupling the queue exists to remove would come back in through the window. An exchange
 * with no queues bound to it discards messages, which is the right behaviour: publishing
 * does not require anybody to be interested.
 *
 * For the consuming side, this service declares its OWN queue, binding and error handling,
 * exactly as notification-service's NotificationQueueConfig does for the cancellation
 * event - the same converter is JSON and not Java's default serialisation, so the messages
 * stay readable in the broker console and neither side is tied to the other's compiled
 * classes.
 */
@Configuration
public class EventTopologyConfig {

    @Bean
    TopicExchange eventsExchange() {
        // durable: it survives a broker restart, like the queues on both sides.
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== consumer: UserDeletedEvent ====================

    @Bean
    Queue userDeletedQueue() {
        return new Queue(EventTopology.USER_DELETED_BOOKINGS_QUEUE, true);
    }

    @Bean
    Binding userDeletedBinding(Queue userDeletedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(userDeletedQueue)
                .to(eventsExchange)
                .with(EventTopology.ROUTING_KEY_USER_DELETED);
    }

    // ==================== recovery of messages that cannot be handled ====================

    @Bean
    Queue userDeletedErrorQueue() {
        return new Queue(EventTopology.USER_DELETED_BOOKINGS_ERROR_QUEUE, true);
    }

    @Bean
    DirectExchange errorExchange() {
        return new DirectExchange(EventTopology.ERROR_EXCHANGE, true, false);
    }

    @Bean
    Binding userDeletedErrorBinding(Queue userDeletedErrorQueue, DirectExchange errorExchange) {
        return BindingBuilder.bind(userDeletedErrorQueue)
                .to(errorExchange)
                .with(EventTopology.ROUTING_KEY_USER_DELETED_BOOKINGS_FAILED);
    }

    /**
     * Where a UserDeletedEvent that cannot be handled ends up, once its attempts are
     * exhausted. Same reasoning as notification-service's own recoverer: without it a
     * failing message would either loop forever or vanish with no trace of what failed.
     */
    @Bean
    MessageRecoverer failedMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate,
                EventTopology.ERROR_EXCHANGE,
                EventTopology.ROUTING_KEY_USER_DELETED_BOOKINGS_FAILED);
    }
}
