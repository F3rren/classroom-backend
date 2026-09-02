package com.prenotazioni.notifica;

import com.prenotazioni.eventi.PrenotazioneCancellataEvento;
import com.prenotazioni.eventi.TopologiaEventi;
import com.prenotazioni.notifica.model.Notifica;
import com.prenotazioni.notifica.repository.NotificaRepository;
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
 * La notifica di cancellazione arriva come messaggio, non piu' come chiamata REST.
 *
 * NOTA SULL'ANNOTAZIONE: qui NON c'e' disabledWithoutDocker, a differenza delle classi sui
 * vincoli del database. E' deliberato. Un test sulla messaggistica che si auto-disabilita
 * quando il broker manca non prova nulla e lo fa in silenzio, ed e' esattamente il modo in
 * cui in questo progetto quattro asserzioni sono rimaste nascoste per giorni dietro un
 * "verde". Senza Docker questa classe deve FALLIRE, rumorosamente.
 *
 * Cosa si verifica davvero: che un evento pubblicato sull'exchange venga instradato alla
 * coda giusta e diventi una notifica sul database. Copre quindi l'intera topologia -
 * exchange, routing key, binding, converter JSON e listener - e non solo il metodo del
 * consumatore, che si potrebbe chiamare direttamente senza toccare un broker.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MessaggisticaCancellazioniTest {

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
        // Il profilo di test spegne il listener per non far rumore nelle altre classi:
        // qui e' il soggetto della prova e va riacceso.
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificaRepository notificaRepository;

    /** Permette di fermare e riavviare il consumatore, per provare che la coda trattenga. */
    @Autowired
    private RabbitListenerEndpointRegistry registro;

    @BeforeEach
    void setUp() {
        notificaRepository.deleteAll();
    }

    private void pubblica(PrenotazioneCancellataEvento evento) {
        rabbitTemplate.convertAndSend(
                TopologiaEventi.EXCHANGE, TopologiaEventi.ROUTING_KEY_CANCELLAZIONE, evento);
    }

    @Test
    void unEventoPubblicatoDiventaUnaNotifica() {
        pubblica(new PrenotazioneCancellataEvento(
                7L, 42L, "Aula Magna", "Mario Rossi", "2026-12-25", "14:30", "16:30", "Sessione d'esame"));

        // Il consumo e' asincrono: si attende l'effetto invece di dormire un tempo fisso,
        // che sarebbe lento quando va bene e instabile quando va male.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notifica> notifiche = notificaRepository.findAll();
            assertThat(notifiche).hasSize(1);

            Notifica n = notifiche.get(0);
            assertThat(n.getUtenteId()).isEqualTo(7L);
            assertThat(n.getPrenotazioneId()).isEqualTo(42L);
            assertThat(n.getNomeStanza()).isEqualTo("Aula Magna");
            assertThat(n.getAdminNome()).isEqualTo("Mario Rossi");
            assertThat(n.getLetta()).isFalse();
        });
    }

    @Test
    void ilMessaggioSopravviveAlConsumatoreSpento() throws Exception {
        // E' LA ragione per cui questa coda esiste, quindi va provata davvero fermando il
        // consumatore, non solo pubblicando e sperando. Con la vecchia chiamata REST questo
        // messaggio sarebbe andato perso; qui deve aspettare in coda.
        registro.stop();

        pubblica(new PrenotazioneCancellataEvento(
                9L, 99L, "Aula B", "Admin", "2026-01-01", "09:00", "11:00", "Manutenzione"));

        // Con il consumatore fermo non deve succedere nulla: se una notifica comparisse qui,
        // significherebbe che il listener non era davvero spento e il test non proverebbe nulla.
        Thread.sleep(1000);
        assertThat(notificaRepository.findAll())
                .as("con il consumatore fermo la notifica non puo' esistere ancora")
                .isEmpty();

        registro.start();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificaRepository.findAll())
                        .as("alla ripartenza il messaggio deve essere ancora li'")
                        .hasSize(1));
    }

    @Test
    void unEventoSenzaDestinatarioVieneScartatoSenzaBloccareLaCoda() {
        // Un messaggio irreparabile non deve essere rimesso in coda all'infinito: girerebbe
        // per sempre occupando il consumatore e bloccando quelli buoni dietro di se'.
        pubblica(new PrenotazioneCancellataEvento(
                null, 1L, "Aula X", "Admin", "2026-01-01", "09:00", "11:00", "Motivo"));

        // Poi un evento valido: se il primo avesse bloccato la coda, questo non arriverebbe.
        pubblica(new PrenotazioneCancellataEvento(
                11L, 2L, "Aula Y", "Admin", "2026-01-02", "10:00", "12:00", "Motivo"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notifica> notifiche = notificaRepository.findAll();
            assertThat(notifiche)
                    .as("solo l'evento valido deve produrre una notifica")
                    .hasSize(1);
            assertThat(notifiche.get(0).getUtenteId()).isEqualTo(11L);
        });
    }
}
