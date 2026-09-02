package com.prenotazioni.client;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Chiamate verso notifica-service.
 *
 * Due decisioni che vale la pena leggere prima di modificare questa classe.
 *
 * 1) NIENTE ECCEZIONI PROPAGATE. Ogni fallimento viene loggato e ingoiato. Una notifica
 *    e' un effetto collaterale informativo: se il servizio notifiche e' spento, una
 *    cancellazione di prenotazione richiesta da un amministratore deve comunque riuscire.
 *    Il contrario significherebbe aver reso il servizio notifiche una dipendenza critica
 *    di un'operazione che non gli appartiene. Il prezzo, esplicito, e' che una notifica
 *    puo' andare persa senza che nessuno se ne accorga: e' il caso che un broker con
 *    consegna garantita risolvera'.
 *
 * 2) IL TOKEN VIENE INOLTRATO. Non c'e' un'identita' di servizio ne' una chiave condivisa
 *    in piu': si passa lo stesso header Authorization della richiesta in corso. Funziona
 *    perche' entrambe le operazioni sono avviate da un amministratore autenticato, e
 *    notifica-service valida il token da solo senza consultare nessuno.
 */
@Component
public class NotificaClient {

    private static final Logger logger = LoggerFactory.getLogger(NotificaClient.class);

    private final RestClient restClient;
    private final HttpServletRequest richiestaCorrente;

    NotificaClient(RestClient.Builder builder,
                   @Value("${prenotazioni.notifica-service.url:http://localhost:8081}") String baseUrl,
                   HttpServletRequest richiestaCorrente) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.richiestaCorrente = richiestaCorrente;
    }

    /** Notifica all'utente che un admin ha cancellato la sua prenotazione. */
    public void notificaCancellazione(Map<String, Object> corpo) {
        try {
            restClient.post()
                    .uri("/api/notifiche/interne/cancellazione-prenotazione")
                    .header(HttpHeaders.AUTHORIZATION, authorizationCorrente())
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            logger.error("Notifica di cancellazione non inviata per utenteId={}: {}",
                    corpo.get("utenteId"), e.getMessage());
        }
    }

    /** Elimina le notifiche di un utente che sta per essere rimosso. */
    public void eliminaNotificheUtente(Long utenteId) {
        try {
            restClient.delete()
                    .uri("/api/notifiche/interne/utente/{utenteId}", utenteId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationCorrente())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Qui il danno e' piu' concreto che per una notifica mancata: restano righe
            // orfane, che prima la chiave esterna rendeva impossibili. Va loggato come
            // errore perche' richiede una bonifica, non solo una consolazione.
            logger.error("Notifiche dell'utenteId={} non eliminate, possibili righe orfane: {}",
                    utenteId, e.getMessage());
        }
    }

    private String authorizationCorrente() {
        String header = richiestaCorrente.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null ? header : "";
    }
}
