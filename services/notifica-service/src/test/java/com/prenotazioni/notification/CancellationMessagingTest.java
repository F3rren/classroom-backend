package com.prenotazioni.notification;

import com.prenotazioni.events.BookingCancelledEvent;
import com.prenotazioni.events.EventTopology;
import com.prenotazioni.notification.model.Notification;
import com.prenotazioni.notification.repository.NotificationRepository;
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
 * NOTA SULL'ANNOTAZIONE: disabledWithoutDocker c'e', come nelle classi sui vincoli del
 * database. Non e' sempre stato cosi'. Prima non c'era, di proposito, perche' un test che si
 * auto-disabilita quando il broker manca non prova nulla e lo fa in silenzio - ed e'
 * esattamente il modo in cui in questo progetto quattro asserzioni sono rimaste nascoste per
 * giorni dietro un "verde".
 *
 * Quel ragionamento valeva finche' NIENTE si accorgeva del salto. Ora il passo di guardia
 * nella CI cerca da se' le classi @Testcontainers e fallisce se un report dice skipped
 * diverso da zero, se manca, o se contiene zero test: il salto in CI e' impossibile da
 * nascondere. Far fallire anche la build locale non aggiungeva protezione, aggiungeva solo
 * l'impossibilita' di lavorare senza Docker acceso.
 *
 * LA PROTEZIONE E' ORA ALTROVE, non e' sparita: sta in .github/workflows/ci.yml. Toglierla
 * di la' rimetterebbe in piedi il difetto che era costato quattro asserzioni.
 *
 * Cosa si verifica davvero: che un evento pubblicato sull'exchange venga instradato alla
 * coda giusta e diventi una notifica sul database. Copre quindi l'intera topologia -
 * exchange, routing key, binding, converter JSON e listener - e non solo il metodo del
 * consumatore, che si potrebbe chiamare direttamente senza toccare un broker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
// L'UNICA classe che lo tiene, e non per simmetria: uno dei test ferma il registro dei
// listener AMQP con registro.stop() e lo riavvia. Il contesto non torna nello stato di
// partenza da solo, quindi riusarlo per la classe successiva significherebbe consegnarle
// un ascoltatore in uno stato che non ha scelto. Nelle altre quindici era copiato senza
// motivo, e costava una ricostruzione completa del contesto a testa.
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
        // Il profilo di test spegne il listener per non far rumore nelle altre classi:
        // qui e' il soggetto della prova e va riacceso.
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    /** Permette di fermare e riavviare il consumatore, per provare che la coda trattenga. */
    @Autowired
    private RabbitListenerEndpointRegistry registro;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    private void pubblica(BookingCancelledEvent event) {
        rabbitTemplate.convertAndSend(
                EventTopology.EXCHANGE, EventTopology.ROUTING_KEY_CANCELLAZIONE, event);
    }

    @Test
    void unEventoPubblicatoDiventaUnaNotifica() {
        pubblica(new BookingCancelledEvent(
                7L, 42L, "Aula Magna", "Mario Rossi", "2026-12-25", "14:30", "16:30", "Sessione d'esame"));

        // Il consumo e' asincrono: si attende l'effetto invece di dormire un tempo fisso,
        // che sarebbe lento quando va bene e instabile quando va male.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifiche = notificationRepository.findAll();
            assertThat(notifiche).hasSize(1);

            Notification n = notifiche.get(0);
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

        pubblica(new BookingCancelledEvent(
                9L, 99L, "Aula B", "Admin", "2026-01-01", "09:00", "11:00", "Manutenzione"));

        // Con il consumatore fermo non deve succedere nulla: se una notifica comparisse qui,
        // significherebbe che il listener non era davvero spento e il test non proverebbe nulla.
        //
        // during() e non un'attesa fissa: verifica che la condizione resti vera per TUTTA la
        // finestra, invece di dormire e guardare una volta sola alla fine. E' piu' severo -
        // intercetta anche una notifica che comparisse e sparisse - e costa meno della meta'.
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
    void unEventoSenzaDestinatarioVieneScartatoSenzaBloccareLaCoda() {
        // Un messaggio irreparabile non deve essere rimesso in coda all'infinito: girerebbe
        // per sempre occupando il consumatore e bloccando quelli buoni dietro di se'.
        pubblica(new BookingCancelledEvent(
                null, 1L, "Aula X", "Admin", "2026-01-01", "09:00", "11:00", "Motivo"));

        // Poi un evento valido: se il primo avesse bloccato la coda, questo non arriverebbe.
        pubblica(new BookingCancelledEvent(
                11L, 2L, "Aula Y", "Admin", "2026-01-02", "10:00", "12:00", "Motivo"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifiche = notificationRepository.findAll();
            assertThat(notifiche)
                    .as("solo l'evento valido deve produrre una notifica")
                    .hasSize(1);
            assertThat(notifiche.get(0).getUtenteId()).isEqualTo(11L);
        });
    }
}
