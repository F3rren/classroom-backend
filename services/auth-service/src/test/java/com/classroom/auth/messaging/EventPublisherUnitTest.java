package com.classroom.auth.messaging;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.events.EventTopology;
import com.classroom.events.UserDeletedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The publication of the user-deleted event.
 *
 * Mirrors booking-service's EventPublisherUnitTest: the central case is the one on the
 * header, which is what lets a search of the logs follow the same operation across the
 * admin's request here and the cleanup it causes later, on another service and another
 * thread.
 */
class EventPublisherUnitTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final EventPublisher eventPublisher = new EventPublisher(rabbitTemplate);

    private final UserDeletedEvent event = new UserDeletedEvent(7L);

    @AfterEach
    void clean() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Applies to the message the post-processor the publisher handed over. */
    private MessageProperties producedHeaders() {
        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(EventTopology.EXCHANGE),
                eq(EventTopology.ROUTING_KEY_USER_DELETED),
                eq(event),
                postProcessor.capture());

        Message message = new Message(new byte[0], new MessageProperties());
        return postProcessor.getValue().postProcessMessage(message).getMessageProperties();
    }

    @Test
    void carriesTheIdOfTheRequestThatCausedTheEvent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestCorrelationFilter.ATTRIBUTE, "REQ_DALGATEWAY");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        eventPublisher.publishUserDeleted(event);

        assertThat((String) producedHeaders().getHeader(RequestCorrelationFilter.HEADER))
                .isEqualTo("REQ_DALGATEWAY");
    }

    @Test
    void stillCarriesAnIdOutsideOfARequest() {
        // An event can also be born outside an HTTP request. A disconnected id beats none:
        // without one the consumer's log line would have no key at all, and could not even
        // be grouped with itself.
        eventPublisher.publishUserDeleted(event);

        assertThat((String) producedHeaders().getHeader(RequestCorrelationFilter.HEADER))
                .isNotBlank();
    }

    @Test
    void anUnreachableBrokerDoesNotFailTheDeletion() {
        // The user has already been deleted by the time we get here: failing the whole
        // response because the event did not go out would be worse than the damage.
        doThrow(new AmqpException("broker giu'"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class),
                        any(Object.class), any(MessagePostProcessor.class));

        assertThat(eventPublisher.publishUserDeleted(event)).isFalse();
    }
}
