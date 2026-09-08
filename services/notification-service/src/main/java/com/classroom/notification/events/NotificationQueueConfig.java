package com.classroom.notification.events;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import com.classroom.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The consumer side of the topology - this service consumes TWO independent events, each
 * with its own queue and binding, sharing ONE error queue between them.
 *
 * Each queue is declared by this service, because it is its own: a publisher has no business
 * knowing who listens. The exchange is declared by every side that uses it, and that is
 * correct - declaring it is idempotent, and it lets the services start in any order without
 * one failing because another has not been through yet.
 *
 * Every queue is DURABLE and messages are persistent by default with this converter: that is
 * the only reason these queues exist at all. A non-durable queue would lose its messages when
 * the broker restarts, which is precisely the moment they would be needed.
 *
 * ONLY ONE MessageRecoverer BEAN, ON PURPOSE. Spring Boot wires a custom recoverer into the
 * listener container factory only when exactly one MessageRecoverer bean exists in the
 * context (it looks it up with ObjectProvider.getIfUnique()); with two, the lookup is
 * ambiguous, Boot silently falls back to a bare RejectAndDontRequeueRecoverer for BOTH
 * listeners, and an exhausted message is dropped with no trace instead of landing in an
 * error queue - for the cancellation listener too, not only for the new one. That is why
 * the two listeners here share one error queue instead of getting one each.
 */
@Configuration
public class NotificationQueueConfig {

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== consumer: BookingCancelledEvent ====================

    @Bean
    Queue cancellationQueue() {
        return new Queue(EventTopology.CANCELLATION_QUEUE, true);
    }

    @Bean
    Binding cancellationBinding(Queue cancellationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(cancellationQueue)
                .to(eventsExchange)
                .with(EventTopology.ROUTING_KEY_CANCELLATION);
    }

    // ==================== consumer: UserDeletedEvent ====================

    @Bean
    Queue userDeletedQueue() {
        return new Queue(EventTopology.USER_DELETED_NOTIFICATIONS_QUEUE, true);
    }

    @Bean
    Binding userDeletedBinding(Queue userDeletedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(userDeletedQueue)
                .to(eventsExchange)
                .with(EventTopology.ROUTING_KEY_USER_DELETED);
    }

    // ==================== recovery of messages that cannot be handled ====================

    @Bean
    DirectExchange errorExchange() {
        return new DirectExchange(EventTopology.ERROR_EXCHANGE, true, false);
    }

    @Bean
    Queue errorQueue() {
        return new Queue(EventTopology.NOTIFICATION_ERROR_QUEUE, true);
    }

    @Bean
    Binding errorBinding(Queue errorQueue, DirectExchange errorExchange) {
        return BindingBuilder.bind(errorQueue)
                .to(errorExchange)
                .with(EventTopology.ROUTING_KEY_NOTIFICATION_FAILED);
    }

    /**
     * Where a message from EITHER listener ends up once its attempts are exhausted.
     *
     * Without this bean the default behaviour after the retries would be to drop it and
     * nothing more: no infinite loop, but also no trace of what failed.
     * RepublishMessageRecoverer republishes it onto the error queue together with the stack
     * trace of the failure, so it stays there to be looked at and, if needed, put back into
     * circulation.
     *
     * The message is then acknowledged: that is what closes the endless redelivery loop.
     */
    @Bean
    MessageRecoverer failedMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate,
                EventTopology.ERROR_EXCHANGE,
                EventTopology.ROUTING_KEY_NOTIFICATION_FAILED);
    }
}
