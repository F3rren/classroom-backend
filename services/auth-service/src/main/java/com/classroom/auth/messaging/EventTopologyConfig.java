package com.classroom.auth.messaging;

import com.classroom.events.EventTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The publisher side of the topology, for the event this service raises: UserDeletedEvent.
 *
 * It declares only the exchange: the queues belong to the consumers, booking-service and
 * notification-service, exactly as booking-service's own EventTopologyConfig does not
 * declare notification-service's queue for BookingCancelledEvent. Declaring the exchange
 * here too is idempotent, so the three services can start in any order.
 */
@Configuration
public class EventTopologyConfig {

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
