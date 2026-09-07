package com.classroom.booking.messaging;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.events.BookingCancelledEvent;
import com.classroom.events.EventTopology;
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
 * La pubblicazione dell'evento di cancellazione.
 *
 * The central test is the one on the header: it is what lets you read in sequence the HTTP
 * request that cancelled the booking and the notification created afterwards, on another
 * service and another thread. Without it, correlation stops at the service boundary - which
 * is exactly where it starts being useful.
 */
class EventPublisherUnitTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final EventPublisher eventPublisher = new EventPublisher(rabbitTemplate);

    private final BookingCancelledEvent event = new BookingCancelledEvent(
            7L, 42L, "Aula 1", "Admin", "2026-09-03", "09:00", "11:00", "maintenance");

    @AfterEach
    void pulisci() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Applies to the message the post-processor the publisher handed over. */
    private MessageProperties producedHeaders() {
        ArgumentCaptor<MessagePostProcessor> processore = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(EventTopology.EXCHANGE),
                eq(EventTopology.ROUTING_KEY_CANCELLATION),
                eq(event),
                processore.capture());

        Message message = new Message(new byte[0], new MessageProperties());
        return processore.getValue().postProcessMessage(message).getMessageProperties();
    }

    @Test
    void carriesTheIdOfTheRequestThatCausedTheEvent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestCorrelationFilter.ATTRIBUTE, "REQ_DALGATEWAY");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        eventPublisher.publishCancellation(event);

        assertThat((String) producedHeaders().getHeader(RequestCorrelationFilter.HEADER))
                .isEqualTo("REQ_DALGATEWAY");
    }

    @Test
    void stillCarriesAnIdOutsideOfARequest() {
        // An event can also be born outside an HTTP request. A disconnected id beats none:
        // without one the consumer's log line would have no key at all, and could not even
        // be grouped with itself.
        eventPublisher.publishCancellation(event);

        assertThat((String) producedHeaders().getHeader(RequestCorrelationFilter.HEADER))
                .isNotBlank();
    }

    @Test
    void anUnreachableBrokerDoesNotFailTheCancellation() {
        // The booking has already been cancelled by the time we get here: failing the whole
        // thing because the notification did not go out would be worse than the damage.
        doThrow(new AmqpException("broker giu'"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class),
                        any(Object.class), any(MessagePostProcessor.class));

        assertThat(eventPublisher.publishCancellation(event)).isFalse();
    }
}
