package com.prenotazioni.auth;

import com.prenotazioni.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The domain constraint on the role, against a real PostgreSQL.
 *
 * It used to live in PostgresSchemaConstraintsTest, in the application module, alongside the
 * constraints on rooms and bookings. It followed the table it constrains: after migration V5
 * that table no longer exists in the application database.
 *
 * Why H2 is not enough: a CHECK constraint is precisely what H2 does not apply the same way,
 * so a test on H2 would pass even with the constraint missing.
 *
 * The second test is the more useful of the two: it loops over the enum values and demands
 * that the database accept every one. That is what catches the realistic regression, namely
 * adding a constant to Role and forgetting the matching migration.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("pgtest")
class UserConstraintsTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("prenotazione_aule_utenti_test")
                    .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off")
                    .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // A method reference and not a direct call: if the class were disabled for lack of
        // Docker, calling getJdbcUrl() here would fail instead of skipping.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    private void inserisci(String username, String role) {
        jdbc.update("INSERT INTO utenti (username, password, nome, email, ruolo, data_registrazione) "
                        + "VALUES (?, 'hash', 'Nome', ?, ?, now())",
                username, username + "@test.it", role);
    }

    @Test
    void theMigrationCreatedTheTableAndTheConstraint() {
        List<String> vincoli = jdbc.queryForList(
                "SELECT con.conname FROM pg_constraint con JOIN pg_class rel ON rel.oid = con.conrelid "
                        + "WHERE rel.relname = 'utenti' AND con.contype = 'c'", String.class);

        assertThat(vincoli).contains("user_role_check");
    }

    @Test
    void theCheckRejectsARoleOutsideTheDomain() {
        // A raw insert and not one through the entity: the enum could not produce this
        // value, so the constraint has to be tested by stepping around the application.
        assertThatThrownBy(() -> inserisci("superuser", "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("user_role_check");
    }

    @Test
    void theCheckAlsoRejectsTheUppercaseFormOfAValidRole() {
        // The converter writes lowercase: if somebody bypassed it, the database has to
        // notice rather than accept two spellings of the same role.
        assertThatThrownBy(() -> inserisci("admin-maiuscolo", "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void everyEnumValueIsAcceptedByTheConstraint() {
        for (Role r : Role.values()) {
            assertThatCode(() -> inserisci("utente-" + r.name().toLowerCase(), r.getValue()))
                    .as("il valore '%s' dell'enum Ruolo deve essere accettato dal CHECK", r.getValue())
                    .doesNotThrowAnyException();
        }
    }
}
