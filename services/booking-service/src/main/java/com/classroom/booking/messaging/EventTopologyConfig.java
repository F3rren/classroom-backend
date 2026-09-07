package com.classroom.booking.messaging;

import com.classroom.events.EventTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The publisher side of the topology.
 *
 * It declares only the exchange: the queue belongs to the consumer, and notification-service
 * is the one that creates it. That is deliberate - if the booking service declared the queue
 * too it would know who is listening, and the coupling the queue exists to remove would come
 * back in through the window..
 * An exchange with no queues bound to it discards messages, which is the right behaviour:
 * publishing does not require anybody to be interested.
 *
 * The converter is JSON and not Java's default serialisation: the messages have to stay
 * readable in the broker console and must not tie the two sides to the same compiled class.
 * Both services know the shared record in shared anyway.
 */
@Configuration
public class EventTopologyConfig {

    @Bean
    TopicExchange eventsExchange() {
        // durable: it survives a broker restart, like the queue on the other side.
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
