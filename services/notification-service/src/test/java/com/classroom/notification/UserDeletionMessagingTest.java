package com.classroom.notification;

import com.classroom.events.EventTopology;
import com.classroom.events.UserDeletedEvent;
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
 * A deleted user's notifications are removed on the reaction to an event now, no longer as
 * a REST call from auth-service.
 *
 * Replaces InternalNotificationTest, whose one test covered
 * DELETE /api/notifications/internal/user/{id} - an endpoint that no longer exists. Mirrors
 * CancellationMessagingTest, this service's other messaging test: what is checked is that a
 * message published on the exchange is routed to this service's own queue and results in
 * the notifications actually being deleted, not just that the listener's method works when
 * called directly.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserDeletionMessagingTest {

    @Container
    @SuppressWarnings("resource")
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
        // A database of its own, not the shared "notifications" every other class in this
        // profile uses: @DirtiesContext closes this context after the class, and
        // ddl-auto=create-drop drops the schema on close. H2's named in-memory databases are
        // a JVM-wide resource keyed only by name, not by Spring context, so that drop would
        // reach into "notifications" and pull the tables out from under whichever other test
        // class's (already-running, cached) context queries it next.
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:userdeletionmessagingtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    /** Lets the consumer be stopped and restarted, to prove the queue holds messages. */
    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    private void publish(UserDeletedEvent event) {
        rabbitTemplate.convertAndSend(
                EventTopology.EXCHANGE, EventTopology.ROUTING_KEY_USER_DELETED, event);
    }

    @Test
    void aPublishedEventDeletesOnlyThatUsersNotifications() {
        notificationRepository.save(new Notification(42L, "Sua", "Messaggio", "INFO"));
        notificationRepository.save(new Notification(42L, "Sua anche questa", "Messaggio", "INFO"));
        notificationRepository.save(new Notification(7L, "Di un altro", "Non toccare", "INFO"));

        publish(new UserDeletedEvent(42L));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> remaining = notificationRepository.findAll();
            assertThat(remaining).extracting(n -> n.getUserId()).containsExactly(7L);
        });
    }

    @Test
    void theMessageSurvivesAStoppedConsumer() {
        // This is THE reason the queue exists, so it has to be tested by actually stopping
        // the consumer, not by publishing and hoping.
        notificationRepository.save(new Notification(9L, "Sua", "Messaggio", "INFO"));
        listenerRegistry.stop();

        publish(new UserDeletedEvent(9L));

        await().during(Duration.ofMillis(400)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .as("with the consumer stopped the notification cannot be deleted yet")
                        .hasSize(1));

        listenerRegistry.start();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .as("on restart the message has to still be there")
                        .isEmpty());
    }

    @Test
    void anEventWithNoUserIdIsDiscardedWithoutBlockingTheQueue() {
        notificationRepository.save(new Notification(13L, "Sua", "Messaggio", "INFO"));

        // An unfixable message must not be requeued forever: it would spin endlessly, tying
        // up the consumer and holding the good ones up behind it.
        publish(new UserDeletedEvent(null));

        // Then a valid event: if the first had blocked the queue, this would never arrive.
        publish(new UserDeletedEvent(13L));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .as("only the valid event may delete a notification")
                        .isEmpty());
    }
}
