package com.classroom.booking.messaging;

import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingOwner;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.RoomStatus;
import com.classroom.booking.repository.BookingRepository;
import com.classroom.booking.repository.RoomRepository;
import com.classroom.events.EventTopology;
import com.classroom.events.UserDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.NonNull;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A deleted user's bookings are removed on the reaction to an event now, no longer as a
 * REST call from auth-service.
 *
 * Mirrors notification-service's CancellationMessagingTest, for the same reasons: what is
 * checked is that a message published on the exchange is routed to booking-service's own
 * queue and results in the bookings actually being deleted - the whole topology, not just
 * the listener's method called directly.
 *
 * disabledWithoutDocker is here for the same reason as there: a messaging test that
 * disables itself when the broker is missing proves nothing, silently. The CI guard step
 * looks for @Testcontainers classes and fails if one was skipped.
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
        // A database of its own, not the shared "testdb" every other class in this profile
        // uses: @DirtiesContext closes this context after the class, and ddl-auto=create-drop
        // drops the schema on close. H2's named in-memory databases are a JVM-wide resource
        // keyed only by name, not by Spring context, so that drop would reach into "testdb"
        // and pull the tables out from under whichever other test class's (already-running,
        // cached) context queries it next - intermittently, depending on the order Surefire
        // happens to run classes in.
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:userdeletionmessagingtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    /** Lets the consumer be stopped and restarted, to prove the queue holds messages. */
    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    private Long roomId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        Room room = new Room();
        room.setName("Aula Messaging");
        room.setFloor(1);
        room.setCapacity(30);
        room.setVirtual(false);
        room.setStatus(RoomStatus.FREE);
        roomId = roomRepository.save(room).getId();
    }

    @NonNull
    private Long bookRoomFor(Long userId) {
        Booking booking = new Booking();
        booking.setRoom(roomRepository.findById(Objects.requireNonNull(roomId)).orElseThrow());
        booking.setUser(new BookingOwner(userId, "user-" + userId, "User " + userId));
        booking.setStartTime(LocalDateTime.now().plusDays(1));
        booking.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        booking.setStatus(BookingStatus.BOOKED);
        booking.setCreatedAt(LocalDateTime.now());
        return Objects.requireNonNull(bookingRepository.save(booking).getId());
    }

    private void publish(UserDeletedEvent event) {
        rabbitTemplate.convertAndSend(
                EventTopology.EXCHANGE, EventTopology.ROUTING_KEY_USER_DELETED, event);
    }

    @Test
    void aPublishedEventDeletesOnlyThatUsersBookings() {
        bookRoomFor(7L);
        bookRoomFor(11L);

        publish(new UserDeletedEvent(7L));

        // Consumption is asynchronous: wait for the effect instead of sleeping a fixed time,
        // which would be slow when things go well and flaky when they do not.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Booking> remaining = bookingRepository.findAll();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getUser().getId()).isEqualTo(11L);
        });
    }

    @Test
    void theMessageSurvivesAStoppedConsumer() {
        // This is THE reason the queue exists, so it has to be tested by actually stopping
        // the consumer, not by publishing and hoping. With the old REST call this message
        // would have failed outright; here it has to wait on the queue.
        Long bookingId = bookRoomFor(9L);
        listenerRegistry.stop();

        publish(new UserDeletedEvent(9L));

        await().during(Duration.ofMillis(400)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(bookingRepository.findById(bookingId))
                        .as("with the consumer stopped the booking cannot be deleted yet")
                        .isPresent());

        listenerRegistry.start();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(bookingRepository.findById(bookingId))
                        .as("on restart the message has to still be there")
                        .isEmpty());
    }

    @Test
    void anEventWithNoUserIdIsDiscardedWithoutBlockingTheQueue() {
        bookRoomFor(13L);

        // An unfixable message must not be requeued forever: it would spin endlessly, tying
        // up the consumer and holding the good ones up behind it.
        publish(new UserDeletedEvent(null));

        // Then a valid event: if the first had blocked the queue, this would never arrive.
        publish(new UserDeletedEvent(13L));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(bookingRepository.findAll())
                        .as("only the valid event may delete a booking")
                        .isEmpty());
    }
}
