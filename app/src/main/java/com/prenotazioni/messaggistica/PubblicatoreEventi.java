package com.prenotazioni.messaggistica;

import com.prenotazioni.eventi.PrenotazioneCancellataEvento;
import com.prenotazioni.eventi.TopologiaEventi;
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
public class PubblicatoreEventi {

    private static final Logger logger = LoggerFactory.getLogger(PubblicatoreEventi.class);

    private final RabbitTemplate rabbitTemplate;

    PubblicatoreEventi(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * @return true se il messaggio e' stato consegnato al broker. Il chiamante non deve
     *         far dipendere da questo l'esito della cancellazione: serve solo a decidere
     *         cosa scrivere nei log.
     */
    public boolean pubblicaCancellazione(PrenotazioneCancellataEvento evento) {
        try {
            rabbitTemplate.convertAndSend(
                    TopologiaEventi.EXCHANGE,
                    TopologiaEventi.ROUTING_KEY_CANCELLAZIONE,
                    evento);
            logger.debug("Evento di cancellazione pubblicato per utenteId={}, prenotazioneId={}",
                    evento.utenteId(), evento.prenotazioneId());
            return true;
        } catch (Exception e) {
            logger.error("Evento di cancellazione NON pubblicato per utenteId={}, prenotazioneId={}: "
                            + "la notifica andra' persa. Causa: {}",
                    evento.utenteId(), evento.prenotazioneId(), e.getMessage());
            return false;
        }
    }
}
