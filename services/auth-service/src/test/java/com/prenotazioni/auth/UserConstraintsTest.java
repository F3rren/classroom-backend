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
 * Il vincolo di dominio sul ruolo, su PostgreSQL vero.
 *
 * Stava in PostgresSchemaConstraintsTest, nel modulo applicativo, insieme ai vincoli su
 * aule e prenotazioni. Ha seguito la tabella che vincola: dopo la migrazione V5 quella
 * tabella nel database di app non esiste piu'.
 *
 * Perche' non basta H2: un CHECK constraint e' proprio cio' che H2 non applica allo stesso
 * modo, quindi un test su H2 passerebbe anche con il vincolo assente.
 *
 * Il secondo test e' il piu' utile dei due: cicla i valori dell'enum e pretende che il
 * database li accetti tutti. E' cio' che intercetta la regressione realistica, cioe'
 * aggiungere una costante a Ruolo e dimenticare la migrazione corrispondente.
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
        // Method reference e non chiamata diretta: se la classe fosse disabilitata per
        // assenza di Docker, invocare qui getJdbcUrl() farebbe fallire invece di saltare.
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
    void laMigrazioneHaCreatoLaTabellaEIlVincolo() {
        List<String> vincoli = jdbc.queryForList(
                "SELECT con.conname FROM pg_constraint con JOIN pg_class rel ON rel.oid = con.conrelid "
                        + "WHERE rel.relname = 'utenti' AND con.contype = 'c'", String.class);

        assertThat(vincoli).contains("user_role_check");
    }

    @Test
    void ilCheckRifiutaUnRuoloFuoriDominio() {
        // Inserimento grezzo e non via entita': l'enum non potrebbe produrre questo valore,
        // quindi il vincolo va provato scavalcando il livello applicativo.
        assertThatThrownBy(() -> inserisci("superuser", "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("user_role_check");
    }

    @Test
    void ilCheckRifiutaAncheLaVersioneMaiuscolaDiUnRuoloValido() {
        // Il converter scrive minuscolo: se qualcuno bypassasse il converter, il database
        // deve accorgersene invece di accettare due grafie dello stesso ruolo.
        assertThatThrownBy(() -> inserisci("admin-maiuscolo", "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ogniValoreDellEnumEAmmessoDalVincolo() {
        for (Role r : Role.values()) {
            assertThatCode(() -> inserisci("utente-" + r.name().toLowerCase(), r.getValue()))
                    .as("il valore '%s' dell'enum Ruolo deve essere accettato dal CHECK", r.getValue())
                    .doesNotThrowAnyException();
        }
    }
}
