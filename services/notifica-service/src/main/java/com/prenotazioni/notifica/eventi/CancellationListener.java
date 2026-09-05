package com.prenotazioni.notifica.eventi;

import org.springframework.messaging.handler.annotation.Header;
import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.eventi.PrenotazioneCancellataEvento;
import com.prenotazioni.eventi.EventTopology;
import com.prenotazioni.notifica.service.NotificaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Trasforma in notifica un evento di cancellazione arrivato dal servizio prenotazioni.
 *
 * Prima era un endpoint REST che quel servizio chiamava direttamente. La differenza pratica
 * e' che adesso questo servizio puo' essere spento senza che nulla vada perso: i messaggi
 * restano in coda e vengono consumati al riavvio.
 *
 * SULLE ECCEZIONI: un errore qui fa rimettere il messaggio in coda, e se l'errore e'
 * permanente il messaggio ricomincia da capo all'infinito, occupando il consumatore. Per
 * questo si distinguono due casi: un messaggio malformato viene scartato e loggato, perche'
 * riprovarlo non lo aggiustera' mai; un errore di scrittura sul database viene propagato,
 * perche' quello si', al prossimo tentativo puo' andare a buon fine.
 */
@Component
public class CancellationListener {

    private static final Logger logger = LoggerFactory.getLogger(CancellationListener.class);

    private final NotificaService notificaService;

    CancellationListener(NotificaService notificaService) {
        this.notificaService = notificaService;
    }

    @RabbitListener(queues = EventTopology.CODA_NOTIFICHE_CANCELLAZIONE)
    public void suCancellazione(
            PrenotazioneCancellataEvento evento,
            @Header(name = RequestCorrelationFilter.INTESTAZIONE, required = false) String idRichiesta) {
        // Rimesso in MDC per la durata del trattamento: e' cio' che permette di leggere in
        // fila la richiesta HTTP che ha annullato la prenotazione e la notifica creata qui,
        // che avviene su un altro servizio, un altro thread e qualche istante dopo.
        // required = false perche' un messaggio pubblicato prima di questa modifica, o da
        // un'altra versione, deve continuare a essere consumato.
        RequestCorrelationFilter.applicaAMdc(idRichiesta);
        try {
            if (evento == null || evento.utenteId() == null) {
                // Scartato di proposito: senza destinatario la notifica non ha a chi andare, e
                // rimetterlo in coda lo farebbe girare per sempre.
                logger.error("Evento di cancellazione scartato perche' privo di destinatario: {}", evento);
                return;
            }

            logger.debug("Evento di cancellazione ricevuto per utenteId={}, prenotazioneId={}",
                    evento.utenteId(), evento.prenotazioneId());

            notificaService.createNotificaCancellazionePrenotazione(
                    evento.utenteId(),
                    evento.prenotazioneId(),
                    evento.nomeStanza(),
                    evento.adminNome(),
                    evento.dataPrenotazione(),
                    evento.oraInizio(),
                    evento.oraFine(),
                    evento.motivo());

            logger.info("Notifica di cancellazione creata da evento per utenteId={}", evento.utenteId());
        } finally {
            RequestCorrelationFilter.svuotaMdc();
        }
    }
}
