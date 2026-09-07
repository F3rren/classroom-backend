package com.classroom.booking;

import com.classroom.booking.model.Room;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.RoomStatus;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.model.BookingOwner;
import com.classroom.booking.repository.RoomRepository;
import com.classroom.booking.repository.CourseRepository;
import com.classroom.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The only test class that runs against a real PostgreSQL, to cover what H2 cannot express
 * and which therefore no other test really checks:
 *
 *  - the exclusion constraint bookings_no_overlap (EXCLUDE USING gist), which is the
 *    protection against concurrent double booking;
 *  - the CHECK constraints on the statuses;
 *  - the fact that the Flyway migrations really do run on an empty database, and that the
 *    entities match the schema they produce (ddl-auto=validate).
 *
 * Until this class existed, those behaviours were covered only by unit tests that MOCK the
 * DataIntegrityViolationException: they prove how the application reacts, not that the
 * constraint exists.
 *
 * The other integration classes stay on H2 and are left alone: they are fast, and their
 * fixtures rely on a schema recreated for every class.
 *
 * Without Docker this class is SKIPPED, not failed (disabledWithoutDocker): the JUnit
 * condition runs before SpringExtension, so the context is not even built.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("pgtest")
class PostgresSchemaConstraintsTest {

    /**
     * An explicit tag: PostgreSQLContainer defaults to 9.6.12, which is far too old.
     * btree_gist ships with the official image and the container user is a superuser, so
     * V1's CREATE EXTENSION works with no adjustment.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("classroom_test")
                    // Line the server's time zone up with the JVM's: the columns are
                    // "timestamp" without a zone and the queries use CURRENT_TIMESTAMP.
                    .withEnv("TZ", TimeZone.getDefault().getID())
                    .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off")
                    .withStartupTimeout(Duration.ofMinutes(3));

    /**
     * A method reference and not POSTGRES.getJdbcUrl(): the value is resolved when the
     * context refreshes, so it does not depend on the order in which JUnit registers the
     * Testcontainers extension and Spring's.
     */
    @DynamicPropertySource
    static void datasourceFromContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment env;

    /**
     * A time base with no nanoseconds, far from now: PostgreSQL truncates timestamps to the
     * microsecond, and a base carrying nanoseconds would make the comparisons brittle. Never
     * use a bare LocalDateTime.now() in this class.
     */
    private static final LocalDateTime BASE = LocalDate.now().plusDays(7).atTime(10, 0);

    private BookingOwner user;
    private Room room;

    @BeforeEach
    void setUp() {
        // Here the schema is created once by Flyway and never recreated, so the deletion
        // order has to respect the foreign keys.
        bookingRepository.deleteAll();
        courseRepository.deleteAll();
        roomRepository.deleteAll();

        // The user no longer exists in this database: the booking keeps only its snapshot
        // of them. The id is an arbitrary value, because no foreign key constrains it any
        // more - which is exactly what V4 made possible.
        user = new BookingOwner(42L, "pg-user", "Utente Postgres");

        room = newRoom("Aula Postgres");
    }

    private Room newRoom(String name) {
        Room a = new Room();
        a.setName(name);
        a.setCapacity(30);
        a.setFloor(1);
        a.setVirtual(false);
        a.setStatus(RoomStatus.FREE);
        return roomRepository.save(a);
    }

    /** saveAndFlush and not save: otherwise the INSERT can be deferred past the assert. */
    private Booking save(Room onRoom, LocalDateTime startTime, LocalDateTime endTime, BookingStatus status) {
        Booking p = new Booking();
        p.setRoom(onRoom);
        p.setUser(user);
        p.setStartTime(startTime);
        p.setEndTime(endTime);
        p.setStatus(status);
        p.setCreatedAt(BASE.minusDays(1));
        return bookingRepository.saveAndFlush(p);
    }

    // ==================== 1. the migrations really were applied ====================

    @Test
    void flywayAppliedEveryMigrationWithoutBaselining() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);
        // V3 and V5 hand notifications and users over to their own services, V4
        // denormalises the booking's owner. If one were missing, the schema here would be
        // the monolith's and the tests below would be exercising the wrong system.
        // V6 to V8 move the schema itself to English: the tables and columns, then the
        // names PostgreSQL generated on its own, then the status values.
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");

        // No BASELINE row: that is what proves V1 was EXECUTED and not merely marked as
        // already applied, which would have skipped the exclusion constraint.
        Integer baseline = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'BASELINE'", Integer.class);
        assertThat(baseline).isZero();
    }

    @Test
    void hibernateValidatesTheSchemaFlywayProduced() {
        // The real assertion is structural: if the entities drifted from the migrations the
        // context would not start and the whole class would error out. This test makes
        // explicit a guarantee that would otherwise stay invisible.
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void theExtensionAndTheConstraintsArePresentInTheCatalogue() {
        Integer gist = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);
        assertThat(gist).isEqualTo(1);

        List<String> constraints = jdbc.queryForList(
                "SELECT conname FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE c.contype IN ('c','x') AND t.relname IN ('rooms','bookings')",
                String.class);
        assertThat(constraints).containsExactlyInAnyOrder(
                // user_role_check is no longer here: it lives in auth-service's database,
                // alongside the table it constrains. A twin test over there checks it.
                "bookings_no_overlap", "room_status_check", "booking_status_check");
    }

    @Test
    void theDatasourcePointsAtTheContainerAndNotTheLocalDatabase() {
        // If .env could override @DynamicPropertySource, these tests would run against the
        // development database. Better to find that out here.
        assertThat(env.getProperty("spring.datasource.url"))
                .contains(String.valueOf(POSTGRES.getFirstMappedPort()));
    }

    // ==================== 2. the exclusion constraint ====================

    @Test
    void twoOverlappingBookingsForTheSameRoomAreRejectedByTheDatabase() {
        save(room, BASE, BASE.plusHours(2), BookingStatus.BOOKED);

        assertThatThrownBy(() -> save(room, BASE.plusHours(1), BASE.plusHours(3), BookingStatus.BOOKED))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bookings_no_overlap");
    }

    @Test
    void backToBackBookingsAreAllowed() {
        save(room, BASE, BASE.plusHours(2), BookingStatus.BOOKED);

        // tsrange is half-open: [10,12) and [12,14) do not overlap.
        assertThatCode(() -> save(room, BASE.plusHours(2), BASE.plusHours(4), BookingStatus.BOOKED))
                .doesNotThrowAnyException();

        // and the application predicate agrees
        assertThat(bookingRepository.findConflictingBookings(
                room.getId(), BASE.plusHours(2), BASE.plusHours(4))).hasSize(1);
    }

    @Test
    void aCancelledBookingDoesNotBlockTheSameInterval() {
        save(room, BASE, BASE.plusHours(2), BookingStatus.CANCELLED);

        // It also proves indirectly that the converter writes "cancelled" in lowercase:
        // the constraint filters with WHERE status <> 'cancelled', so if the converter wrote
        // the constant's name instead, this insert would be refused.
        assertThatCode(() -> save(room, BASE, BASE.plusHours(2), BookingStatus.BOOKED))
                .doesNotThrowAnyException();
    }

    @Test
    void theSameIntervalOnDifferentRoomsIsAllowed() {
        Room otherRoom = newRoom("Aula Postgres 2");
        save(room, BASE, BASE.plusHours(2), BookingStatus.BOOKED);

        assertThatCode(() -> save(otherRoom, BASE, BASE.plusHours(2), BookingStatus.BOOKED))
                .doesNotThrowAnyException();
    }

    /**
     * The application predicate (findConflictingBookings) and the database constraint have to
     * agree. Reference interval: [BASE, BASE+120min).
     */
    @ParameterizedTest(name = "offset [{0},{1}) minutes -> conflict expected: {2}")
    @CsvSource({
            "0,   120, true",   // identical
            "30,  90,  true",   // contained
            "-60, 180, true",   // containing
            "-60, 60,  true",   // overlapping on the left
            "60,  180, true",   // overlapping on the right
            "-120, 0,  false",  // adjacent, before
            "120, 240, false",  // adjacent, after
            "-240,-120,false",  // apart, before
            "240, 360, false"   // apart, after
    })
    void theApplicationPredicateAndTheDatabaseConstraintAgree(long fromMin, long toMin, boolean expectedConflict) {
        save(room, BASE, BASE.plusHours(2), BookingStatus.BOOKED);

        LocalDateTime startTime = BASE.plusMinutes(fromMin);
        LocalDateTime endTime = BASE.plusMinutes(toMin);

        boolean applicationConflict =
                !bookingRepository.findConflictingBookings(room.getId(), startTime, endTime).isEmpty();
        assertThat(applicationConflict)
                .as("the application predicate on [%d,%d)", fromMin, toMin)
                .isEqualTo(expectedConflict);

        if (expectedConflict) {
            assertThatThrownBy(() -> save(room, startTime, endTime, BookingStatus.BOOKED))
                    .as("the database has to reject [%d,%d)", fromMin, toMin)
                    .isInstanceOf(DataIntegrityViolationException.class);
        } else {
            assertThatCode(() -> save(room, startTime, endTime, BookingStatus.BOOKED))
                    .as("the database has to accept [%d,%d)", fromMin, toMin)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void aZeroLengthInterval_theDatabaseAllowsItButTheApplicationDoesNot() {
        save(room, BASE, BASE.plusHours(2), BookingStatus.BOOKED);

        // A real, documented divergence: tsrange(t,t) is empty and never overlaps anything,
        // so the database constraint accepts it. The application predicate reports a
        // conflict instead. It is reachable over HTTP: the controller refuses only
        // end < start, not end == start. So only the application layer stops it.
        assertThat(bookingRepository.findConflictingBookings(
                room.getId(), BASE.plusHours(1), BASE.plusHours(1))).isNotEmpty();

        assertThatCode(() -> save(room, BASE.plusHours(1), BASE.plusHours(1), BookingStatus.BOOKED))
                .doesNotThrowAnyException();
    }

    // ==================== 3. the CHECK constraints ====================
    // Raw inserts: the enums cannot produce a value outside the domain.

    // The CHECK on the role is verified in auth-service, which owns the users table: see
    // UserConstraintsTest. The table no longer exists here (migration V5).

    @Test
    void theCheckRejectsARoomStatusOutsideTheDomain() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO rooms (name, capacity, floor, is_virtual, status) "
                        + "VALUES ('Broken Room', 10, 1, false, 'destroyed')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("room_status_check");
    }

    @Test
    void theCheckRejectsABookingStatusOutsideTheDomain() {
        // A far-off interval, so it is the CHECK that trips and not the exclusion constraint.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, created_at) "
                        + "VALUES (?, ?, ?, ?, 'nonsense', ?)",
                room.getId(), user.getId(),
                BASE.plusDays(30), BASE.plusDays(30).plusHours(1), BASE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("booking_status_check");
    }

    @Test
    void everyEnumValueIsAcceptedByTheChecks() {
        // The inverse of the three tests above, and the one that catches the realistic
        // regression: adding a constant to an enum and forgetting the migration.

        for (RoomStatus s : RoomStatus.values()) {
            assertThatCode(() -> jdbc.update(
                    "INSERT INTO rooms (name, capacity, floor, is_virtual, status) VALUES (?, 10, 1, false, ?)",
                    "Room " + s.getValue(), s.getValue()))
                    .as("room status %s must be accepted", s.getValue())
                    .doesNotThrowAnyException();
        }

        int day = 40;
        for (BookingStatus s : BookingStatus.values()) {
            LocalDateTime startTime = BASE.plusDays(day++);
            assertThatCode(() -> jdbc.update(
                    "INSERT INTO bookings (room_id, user_id, start_time, end_time, status, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    room.getId(), user.getId(), startTime, startTime.plusHours(1), s.getValue(), BASE))
                    .as("booking status %s must be accepted", s.getValue())
                    .doesNotThrowAnyException();
        }
    }

}
