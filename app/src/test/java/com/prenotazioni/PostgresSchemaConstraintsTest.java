package com.prenotazioni;

import com.prenotazioni.testsupport.TestJwt;
import com.prenotazioni.model.Aula;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.StatoAula;
import com.prenotazioni.model.StatoPrenotazione;
import com.prenotazioni.model.ProprietarioPrenotazione;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.ICorsoRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
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
 * L'unica classe di test che gira su PostgreSQL reale, per coprire cio' che H2 non
 * sa esprimere e che quindi nessun altro test verifica davvero:
 *
 *  - il vincolo di esclusione prenotazioni_no_overlap (EXCLUDE USING gist), che e'
 *    la protezione contro la doppia prenotazione concorrente;
 *  - i tre CHECK constraint su ruolo e stati;
 *  - il fatto che le migrazioni Flyway girino per davvero su un database vuoto, e
 *    che le entity corrispondano allo schema che producono (ddl-auto=validate).
 *
 * Fino a qui questi comportamenti erano coperti solo da unit test che MOCCANO la
 * DataIntegrityViolationException: provano la reazione dell'applicazione, non che
 * il vincolo esista.
 *
 * Le altre 9 classi di integrazione restano su H2 e non vengono toccate: sono
 * veloci e le loro fixture dipendono da uno schema ricreato a ogni classe.
 *
 * Senza Docker la classe viene SALTATA, non fallita (disabledWithoutDocker): la
 * condizione di JUnit gira prima di SpringExtension, quindi il contesto non viene
 * nemmeno costruito.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("pgtest")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresSchemaConstraintsTest {

    /**
     * Tag esplicito: il default di PostgreSQLContainer e' 9.6.12, troppo vecchio.
     * btree_gist e' incluso nell'immagine ufficiale e l'utente del container e'
     * superuser, quindi il CREATE EXTENSION della V1 funziona senza aggiustamenti.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("prenotazione_aule_test")
                    // Allinea il fuso del server a quello della JVM: le colonne sono
                    // "timestamp" senza fuso e le query usano CURRENT_TIMESTAMP.
                    .withEnv("TZ", TimeZone.getDefault().getID())
                    .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off")
                    .withStartupTimeout(Duration.ofMinutes(3));

    /**
     * Method reference e non POSTGRES.getJdbcUrl(): il valore viene risolto al
     * refresh del contesto, quindi non dipende dall'ordine con cui JUnit registra
     * l'estensione di Testcontainers e quella di Spring.
     */
    @DynamicPropertySource
    static void datasourceDalContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private IAulaRepository aulaRepository;
    @Autowired private IPrenotazioneRepository prenotazioneRepository;
    @Autowired private ICorsoRepository corsoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment env;

    /**
     * Base temporale senza nanosecondi e lontana da adesso: PostgreSQL tronca i
     * timestamp al microsecondo, e una base con nanosecondi renderebbe fragili i
     * confronti. Mai usare LocalDateTime.now() nudo in questa classe.
     */
    private static final LocalDateTime BASE = LocalDate.now().plusDays(7).atTime(10, 0);

    private ProprietarioPrenotazione utente;
    private Aula aula;

    @BeforeEach
    void setUp() {
        // Qui lo schema e' creato una volta da Flyway e non viene mai ricreato,
        // quindi l'ordine di cancellazione deve rispettare le foreign key.
        prenotazioneRepository.deleteAll();
        corsoRepository.deleteAll();
        aulaRepository.deleteAll();

        // L'utente non esiste piu' in questo database: la prenotazione conserva solo
        // la sua istantanea. L'id e' un valore qualunque, perche' nessuna chiave esterna
        // lo vincola piu' - ed e' esattamente cio' che la V4 ha reso possibile.
        utente = new ProprietarioPrenotazione(42L, "pg-user", "Utente Postgres");

        aula = nuovaAula("Aula Postgres");
    }

    private Aula nuovaAula(String nome) {
        Aula a = new Aula();
        a.setNome(nome);
        a.setCapienza(30);
        a.setPiano(1);
        a.setVirtual(false);
        a.setStato(StatoAula.LIBERA);
        return aulaRepository.save(a);
    }

    /** saveAndFlush e non save: altrimenti l'INSERT puo' essere rinviato oltre l'assert. */
    private Prenotazione salva(Aula suAula, LocalDateTime inizio, LocalDateTime fine, StatoPrenotazione stato) {
        Prenotazione p = new Prenotazione();
        p.setAula(suAula);
        p.setUtente(utente);
        p.setInizio(inizio);
        p.setFine(fine);
        p.setStato(stato);
        p.setDataCreazione(BASE.minusDays(1));
        return prenotazioneRepository.saveAndFlush(p);
    }

    // ==================== 1. le migrazioni sono state applicate davvero ====================

    @Test
    void flywayHaApplicatoTutteLeMigrazioniSenzaBaseline() {
        List<String> versioni = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);
        // V3 e V5 cedono notifiche e utenti ai servizi dedicati, V4 denormalizza il
        // proprietario della prenotazione. Se una mancasse, lo schema qui sarebbe
        // quello del monolite e i test sotto proverebbero il sistema sbagliato.
        assertThat(versioni).containsExactly("1", "2", "3", "4", "5");

        // Nessuna riga BASELINE: e' questo che prova che la V1 e' stata ESEGUITA
        // e non solo marcata come gia' applicata, saltando il vincolo di esclusione.
        Integer baseline = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'BASELINE'", Integer.class);
        assertThat(baseline).isZero();
    }

    @Test
    void hibernateValidaLoSchemaProdottoDaFlyway() {
        // L'assert reale e' strutturale: se le entity divergessero dalle migrazioni,
        // il contesto non si avvierebbe e tutta la classe andrebbe in errore. Questo
        // test rende esplicita una garanzia che altrimenti resterebbe invisibile.
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void estensioneEVincoliSonoPresentiNelCatalogo() {
        Integer gist = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);
        assertThat(gist).isEqualTo(1);

        List<String> vincoli = jdbc.queryForList(
                "SELECT conname FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE c.contype IN ('c','x') AND t.relname IN ('utenti','aule','prenotazioni')",
                String.class);
        assertThat(vincoli).containsExactlyInAnyOrder(
                // utente_ruolo_check non compare piu': vive nel database di auth-service,
                // insieme alla tabella che vincola. Lo verifica un test omologo la'.
                "prenotazioni_no_overlap", "aula_stato_check", "prenotazione_stato_check");
    }

    @Test
    void ilDatasourcePuntaAlContainerENonAlDatabaseLocale() {
        // Se config/config.properties riuscisse a scavalcare @DynamicPropertySource,
        // questi test girerebbero sul database di sviluppo. Meglio accorgersene qui.
        assertThat(env.getProperty("spring.datasource.url"))
                .contains(String.valueOf(POSTGRES.getFirstMappedPort()));
    }

    // ==================== 2. il vincolo di esclusione ====================

    @Test
    void duePrenotazioniSovrappostePerLaStessaAulaSonoRifiutateDalDatabase() {
        salva(aula, BASE, BASE.plusHours(2), StatoPrenotazione.PRENOTATA);

        assertThatThrownBy(() -> salva(aula, BASE.plusHours(1), BASE.plusHours(3), StatoPrenotazione.PRENOTATA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("prenotazioni_no_overlap");
    }

    @Test
    void prenotazioniConsecutiveSonoAmmesse() {
        salva(aula, BASE, BASE.plusHours(2), StatoPrenotazione.PRENOTATA);

        // tsrange e' semiaperto: [10,12) e [12,14) non si sovrappongono.
        assertThatCode(() -> salva(aula, BASE.plusHours(2), BASE.plusHours(4), StatoPrenotazione.PRENOTATA))
                .doesNotThrowAnyException();

        // e il predicato applicativo concorda
        assertThat(prenotazioneRepository.findConflittingReservations(
                aula.getId(), BASE.plusHours(2), BASE.plusHours(4))).hasSize(1);
    }

    @Test
    void unaPrenotazioneAnnullataNonBloccaLoStessoIntervallo() {
        salva(aula, BASE, BASE.plusHours(2), StatoPrenotazione.ANNULLATA);

        // Prova indirettamente anche che il converter scrive "annullata" minuscolo:
        // il vincolo filtra con WHERE stato <> 'annullata', quindi se il converter
        // scrivesse il nome della costante questo inserimento verrebbe rifiutato.
        assertThatCode(() -> salva(aula, BASE, BASE.plusHours(2), StatoPrenotazione.PRENOTATA))
                .doesNotThrowAnyException();
    }

    @Test
    void loStessoIntervalloSuAuleDiverseEAmmesso() {
        Aula altra = nuovaAula("Aula Postgres 2");
        salva(aula, BASE, BASE.plusHours(2), StatoPrenotazione.PRENOTATA);

        assertThatCode(() -> salva(altra, BASE, BASE.plusHours(2), StatoPrenotazione.PRENOTATA))
                .doesNotThrowAnyException();
    }

    /**
     * Il predicato applicativo (findConflittingReservations) e quello del vincolo DB
     * devono concordare. Riferimento: [BASE, BASE+120min).
     */
    @ParameterizedTest(name = "offset [{0},{1}) minuti -> conflitto atteso: {2}")
    @CsvSource({
            "0,   120, true",   // identico
            "30,  90,  true",   // contenuto
            "-60, 180, true",   // contenitore
            "-60, 60,  true",   // sovrapposto a sinistra
            "60,  180, true",   // sovrapposto a destra
            "-120, 0,  false",  // adiacente prima
            "120, 240, false",  // adiacente dopo
            "-240,-120,false",  // staccato prima
            "240, 360, false"   // staccato dopo
    })
    void predicatoApplicativoEVincoloDbConcordano(long daMin, long aMin, boolean conflittoAtteso) {
        salva(aula, BASE, BASE.plusHours(2), StatoPrenotazione.PRENOTATA);

        LocalDateTime inizio = BASE.plusMinutes(daMin);
        LocalDateTime fine = BASE.plusMinutes(aMin);

        boolean conflittoApplicativo =
                !prenotazioneRepository.findConflittingReservations(aula.getId(), inizio, fine).isEmpty();
        assertThat(conflittoApplicativo)
                .as("predicato applicativo per [%d,%d)", daMin, aMin)
                .isEqualTo(conflittoAtteso);

        if (conflittoAtteso) {
            assertThatThrownBy(() -> salva(aula, inizio, fine, StatoPrenotazione.PRENOTATA))
                    .as("il database deve rifiutare [%d,%d)", daMin, aMin)
                    .isInstanceOf(DataIntegrityViolationException.class);
        } else {
            assertThatCode(() -> salva(aula, inizio, fine, StatoPrenotazione.PRENOTATA))
                    .as("il database deve accettare [%d,%d)", daMin, aMin)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void intervalloDiDurataZero_ilDatabaseLoAmmetteMaLApplicazioneNo() {
        salva(aula, BASE, BASE.plusHours(2), StatoPrenotazione.PRENOTATA);

        // Divergenza reale e documentata: tsrange(t,t) e' vuoto e non si sovrappone
        // mai, quindi il vincolo DB accetta. Il predicato applicativo invece segnala
        // conflitto. E' raggiungibile via HTTP: il controller rifiuta solo fine < inizio,
        // non fine == inizio. A fermarlo e' quindi solo il livello applicativo.
        assertThat(prenotazioneRepository.findConflittingReservations(
                aula.getId(), BASE.plusHours(1), BASE.plusHours(1))).isNotEmpty();

        assertThatCode(() -> salva(aula, BASE.plusHours(1), BASE.plusHours(1), StatoPrenotazione.PRENOTATA))
                .doesNotThrowAnyException();
    }

    // ==================== 3. i CHECK constraint ====================
    // Inserimenti raw: gli enum non possono produrre valori fuori dominio.

    // Il CHECK sul ruolo e' verificato in auth-service, che possiede la tabella utenti:
    // vedi VincoliUtentiTest. Qui la tabella non esiste piu' (migrazione V5).

    @Test
    void ilCheckRifiutaUnoStatoAulaFuoriDominio() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO aule (nome, capienza, piano, is_virtual, stato) "
                        + "VALUES ('Aula Rotta', 10, 1, false, 'distrutta')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("aula_stato_check");
    }

    @Test
    void ilCheckRifiutaUnoStatoPrenotazioneFuoriDominio() {
        // Intervallo lontano, cosi' e' il CHECK a scattare e non il vincolo di esclusione.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO prenotazioni (aula_id, utente_id, inizio, fine, stato, data_creazione) "
                        + "VALUES (?, ?, ?, ?, 'pippo', ?)",
                aula.getId(), utente.getId(),
                BASE.plusDays(30), BASE.plusDays(30).plusHours(1), BASE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("prenotazione_stato_check");
    }

    @Test
    void tuttiIValoriDegliEnumSonoAmmessiDaiCheck() {
        // L'inverso dei tre test sopra, ed e' quello che intercetta la regressione
        // realistica: aggiungere una costante a un enum e dimenticare la migrazione.

        for (StatoAula s : StatoAula.values()) {
            assertThatCode(() -> jdbc.update(
                    "INSERT INTO aule (nome, capienza, piano, is_virtual, stato) VALUES (?, 10, 1, false, ?)",
                    "Aula " + s.getValore(), s.getValore()))
                    .as("stato aula %s deve essere ammesso", s.getValore())
                    .doesNotThrowAnyException();
        }

        int giorno = 40;
        for (StatoPrenotazione s : StatoPrenotazione.values()) {
            LocalDateTime inizio = BASE.plusDays(giorno++);
            assertThatCode(() -> jdbc.update(
                    "INSERT INTO prenotazioni (aula_id, utente_id, inizio, fine, stato, data_creazione) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    aula.getId(), utente.getId(), inizio, inizio.plusHours(1), s.getValore(), BASE))
                    .as("stato prenotazione %s deve essere ammesso", s.getValore())
                    .doesNotThrowAnyException();
        }
    }

}
