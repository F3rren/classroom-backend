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
 * The consumer side of the topology.
 *
 * The queue is declared by this service, because it is its own: a publisher has no business
 * knowing who listens. The exchange is declared by both, and that is correct - declaring it
 * is idempotent, and it lets the two services start in either order without the first one
 * failing because the other has not been through yet.
 *
 * The queue is DURABLE and messages are persistent by default with this converter: that is
 * the only reason this queue exists at all. A non-durable queue would lose its messages when
 * the broker restarts, which is precisely the moment it would be needed.
 */
@Configuration
public class NotificationQueueConfig {

    @Bean
    Queue cancellationQueue() {
        return new Queue(EventTopology.CANCELLATION_QUEUE, true);
    }

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    Binding cancellationBinding(Queue cancellationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(cancellationQueue)
                .to(eventsExchange)
                .with(EventTopology.ROUTING_KEY_CANCELLATION);
    }

    @Bean
    MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== recovery of messages that cannot be handled ====================

    @Bean
    Queue errorQueue() {
        return new Queue(EventTopology.CANCELLATION_ERROR_QUEUE, true);
    }

    @Bean
    DirectExchange errorExchange() {
        return new DirectExchange(EventTopology.ERROR_EXCHANGE, true, false);
    }

    @Bean
    Binding errorBinding(Queue errorQueue, DirectExchange errorExchange) {
        return BindingBuilder.bind(errorQueue)
                .to(errorExchange)
                .with(EventTopology.ROUTING_KEY_CANCELLATION_FAILED);
    }

    /**
     * Where a message ends up once its attempts are exhausted.
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
                EventTopology.ROUTING_KEY_CANCELLATION_FAILED);
    }
}
