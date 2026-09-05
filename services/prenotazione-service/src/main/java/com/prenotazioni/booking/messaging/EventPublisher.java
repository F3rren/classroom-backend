package com.prenotazioni.booking.messaging;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.events.BookingCancelledEvent;
import com.prenotazioni.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Pubblica sul broker gli eventi che altri servizi possono voler sapere.
 *
 * Prima la notifica di cancellazione era una chiamata REST diretta a notifica-service. Il
 * problema non era la latenza ma la perdita: se quel servizio era spento, la notifica
 * spariva - loggata e ingoiata, perche' un errore li' non deve certo annullare una
 * cancellazione gia' avvenuta.
 *
 * Con la coda la dipendenza si sposta dal servizio al broker, ed e' un guadagno concreto e
 * non un abbellimento: notifica-service puo' essere spento, riavviato o in aggiornamento
 * senza che nessuna notifica vada persa, perche' il messaggio la aspetta in coda.
 *
 * Cosa NON risolve, e va detto: se il broker e' irraggiungibile la pubblicazione fallisce e
 * il messaggio si perde comunque. La finestra si e' ristretta, non chiusa. Per questo il
 * fallimento resta loggato come errore e non propagato: la prenotazione e' gia' stata
 * cancellata, e far fallire la risposta all'admin non la farebbe tornare indietro.
 */
@Component
public class EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * @return true se il messaggio e' stato consegnato al broker. Il chiamante non deve
     *         far dipendere da questo l'esito della cancellazione: serve solo a decidere
     *         cosa scrivere nei log.
     */
    public boolean publishCancellation(BookingCancelledEvent event) {
        try {
            // L'identificativo viaggia come intestazione del messaggio e non come campo
            // del record: il payload e' un contratto fra due servizi, e non va allargato
            // per un dato che serve solo a leggere i log. Cosi' la notifica creata piu'
            // tardi dal consumatore si ricollega alla cancellazione che l'ha causata.
            String requestId = RequestCorrelationFilter.current();
            rabbitTemplate.convertAndSend(
                    EventTopology.EXCHANGE,
                    EventTopology.ROUTING_KEY_CANCELLATION,
                    event,
                    message -> {
                        message.getMessageProperties()
                                .setHeader(RequestCorrelationFilter.INTESTAZIONE, requestId);
                        return message;
                    });
            logger.debug("Evento di cancellazione pubblicato per utenteId={}, prenotazioneId={}",
                    event.userId(), event.bookingId());
            return true;
        } catch (Exception e) {
            logger.error("Evento di cancellazione NON pubblicato per utenteId={}, prenotazioneId={}: "
                            + "la notifica andra' persa. Causa: {}",
                    event.userId(), event.bookingId(), e.getMessage());
            return false;
        }
    }
}
