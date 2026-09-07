package com.classroom.notification;

import com.classroom.events.BookingCancelledEvent;
import com.classroom.events.EventTopology;
import com.classroom.notification.model.Notification;
import com.classroom.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The cancellation notification arrives as a message now, no longer as a REST call.
 *
 * A NOTE ON THE ANNOTATION: disabledWithoutDocker is here, as in the database-constraint
 * classes. It has not always been. It was deliberately absent at first, because a test that
 * disables itself when the broker is missing proves nothing and does so silently - and that
 * is exactly how four assertions in this project stayed hidden behind a "green" for days.
 *
 * That reasoning held as long as NOTHING noticed the skip. Now the guard step in CI looks
 * for the @Testcontainers classes itself and fails if a report says skipped is not zero, if
 * it is missing, or if it contains no tests: skipping in CI is impossible to hide. Failing
 * the local build as well added no protection, it only added the impossibility of working
 * without Docker running.
 *
 * THE PROTECTION HAS MOVED, it has not gone: it lives in .github/workflows/ci.yml. Removing
 * it from there would put back the defect that had cost four assertions.
 *
 * What is actually checked: that an event published on the exchange is routed to the right
 * queue and becomes a notification in the database. It therefore covers the whole topology -
 * exchange, routing key, binding, JSON converter and listener - and not just the consumer's
 * method, which could be called directly without ever touching a broker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
// The ONLY class that keeps it, and not out of symmetry: one of the tests stops the AMQP
// listener registry and starts it again. The context does not return to its initial state on
// its own, so reusing it for the next class would hand that class a listener in a state it
// did not choose. In the other fifteen it had been copied for no reason, and cost a full
// context rebuild each.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CancellationMessagingTest {

    @Container
    static final RabbitMQContainer BROKER =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", BROKER::getHost);
        registry.add("spring.rabbitmq.port", BROKER::getAmqpPort);
        registry.add("spring.rabbitmq.username", BROKER::getAdminUsername);
        registry.add("spring.rabbitmq.password", BROKER::getAdminPassword);
        // The test profile switches the listener off so it makes no noise in the other
        // classes: here it is the subject of the test and has to be switched back on.
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    /** Lets the consumer be stopped and restarted, to prove the queue holds messages. */
    @Autowired
    private RabbitListenerEndpointRegistry registro;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    private void pubblica(BookingCancelledEvent event) {
        rabbitTemplate.convertAndSend(
                EventTopology.EXCHANGE, EventTopology.ROUTING_KEY_CANCELLATION, event);
    }

    @Test
    void aPublishedEventBecomesANotification() {
        pubblica(new BookingCancelledEvent(
                7L, 42L, "Aula Magna", "Mario Rossi", "2026-12-25", "14:30", "16:30", "Sessione d'esame"));

        // Consumption is asynchronous: wait for the effect instead of sleeping a fixed time,
        // which would be slow when things go well and flaky when they do not.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);

            Notification n = notifications.get(0);
            assertThat(n.getUserId()).isEqualTo(7L);
            assertThat(n.getBookingId()).isEqualTo(42L);
            assertThat(n.getRoomName()).isEqualTo("Aula Magna");
            assertThat(n.getAdminName()).isEqualTo("Mario Rossi");
            assertThat(n.getRead()).isFalse();
        });
    }

    @Test
    void theMessageSurvivesAStoppedConsumer() throws Exception {
        // This is THE reason the queue exists, so it has to be tested by actually stopping
        // the consumer, not by publishing and hoping. With the old REST call this message
        // would have been lost; here it has to wait on the queue.
        registro.stop();

        pubblica(new BookingCancelledEvent(
                9L, 99L, "Aula B", "Admin", "2026-01-01", "09:00", "11:00", "Manutenzione"));

        // With the consumer stopped nothing must happen: a notification appearing here would
        // mean the listener was not really off, and the test would prove nothing.
        //
        // during() rather than a fixed wait: it checks the condition holds for the WHOLE
        // window, instead of sleeping and looking once at the end. It is stricter - it also
        // catches a notification that appeared and vanished - and costs less than half.
        await().during(Duration.ofMillis(400)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .as("con il consumatore fermo la notifica non puo' esistere ancora")
                        .isEmpty());

        registro.start();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .as("alla ripartenza il messaggio deve essere ancora li'")
                        .hasSize(1));
    }

    @Test
    void anEventWithNoRecipientIsDiscardedWithoutBlockingTheQueue() {
        // An unfixable message must not be requeued forever: it would spin endlessly, tying
        // up the consumer and holding the good ones up behind it.
        pubblica(new BookingCancelledEvent(
                null, 1L, "Aula X", "Admin", "2026-01-01", "09:00", "11:00", "Motivo"));

        // Then a valid event: if the first had blocked the queue, this would never arrive.
        pubblica(new BookingCancelledEvent(
                11L, 2L, "Aula Y", "Admin", "2026-01-02", "10:00", "12:00", "Motivo"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications)
                    .as("solo l'evento valido deve produrre una notifica")
                    .hasSize(1);
            assertThat(notifications.get(0).getUserId()).isEqualTo(11L);
        });
    }
}
