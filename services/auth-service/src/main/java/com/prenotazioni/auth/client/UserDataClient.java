package com.prenotazioni.auth.client;

import java.util.List;
import java.util.ArrayList;
import org.springframework.web.client.HttpClientErrorException;
import com.prenotazioni.config.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cancella cio' che appartiene a un utente ma vive in altri servizi.
 *
 * Finche' tutto stava in un database, UtenteService cancellava notifiche, prenotazioni e
 * utente dentro una sola transazione, e le chiavi esterne garantivano che non restasse
 * nulla di orfano. Adesso quelle righe stanno in due database che questo servizio non
 * puo' toccare, quindi la cancellazione diventa una sequenza di chiamate:
 *
 *   - NON e' atomica. Se una fallisce, l'utente puo' sparire lasciando dietro le sue
 *     prenotazioni. La finestra non e' teorica.
 *   - Per questo ogni fallimento e' loggato come ERROR e non come warning: richiede una
 *     bonifica, non una consolazione.
 *   - L'ordine e' voluto: prima i dati dipendenti, poi l'utente. Al contrario, un errore
 *     dopo aver rimosso l'utente lascerebbe righe di cui non si sa piu' a chi appartengano.
 *
 * Il token dell'admin che ha chiesto la cancellazione viene inoltrato cosi' com'e': i
 * servizi a valle lo verificano da soli e pretendono il ruolo ADMIN, quindi non esiste una
 * corsia privilegiata fra servizi da proteggere separatamente.
 */
@Component
public class UserDataClient {

    private static final Logger logger = LoggerFactory.getLogger(UserDataClient.class);

    /** Tre tentativi: il primo, piu' due per i guasti che durano meno di un secondo. */
    private static final int TENTATIVI = 3;

    /** Cresce a ogni giro (0.2s, 0.4s): un servizio che riavvia non torna in un istante. */
    private static final long ATTESA_INIZIALE_MS = 200;

    private final RestClient notifications;
    private final RestClient bookings;
    private final HttpServletRequest currentRequest;

    UserDataClient(RestClient.Builder builder,
                     @Value("${prenotazioni.notifica-service.url:http://localhost:17104}") String notificationsUrl,
                     @Value("${prenotazioni.prenotazione-service.url:http://localhost:17103}") String bookingsUrl,
                     HttpServletRequest currentRequest) {
        this.notifications = builder.clone().baseUrl(notificationsUrl).build();
        this.bookings = builder.clone().baseUrl(bookingsUrl).build();
        this.currentRequest = currentRequest;
    }

    /**
     * Cancella i dati dell'utente negli altri servizi.
     *
     * @return i nomi dei dati che NON si e' riusciti a cancellare, vuoto se e' andato tutto.
     *         Un booleano non bastava: chi chiama deve poter dire nel messaggio d'errore
     *         cosa e' rimasto indietro, altrimenti l'unica informazione e' "qualcosa e'
     *         fallito" e chi ripete non sa cosa aspettarsi.
     */
    public List<String> deleteDataOf(Long userId) {
        List<String> failed = new ArrayList<>();
        // Entrambe le chiamate vengono tentate anche se la prima fallisce: fermarsi
        // lascerebbe piu' roba indietro senza dire di piu' a chi legge l'errore.
        if (!delete(notifications, "/api/notifications/internal/user/{id}", userId, "notifications")) {
            failed.add("notifications");
        }
        if (!delete(bookings, "/api/bookings/internal/user/{id}", userId, "bookings")) {
            failed.add("bookings");
        }
        return failed;
    }

    /**
     * Un DELETE con qualche tentativo, perche' la maggior parte dei guasti qui e' passeggera.
     *
     * Un servizio che sta riavviando, una connessione rifiutata per un istante, un 5xx
     * momentaneo: al primo colpo falliscono, al secondo spesso no. Senza tentativi ognuno di
     * questi lasciava l'operazione a meta' e la sua conclusione dipendeva da un essere umano
     * che se ne accorgesse e la ripetesse.
     *
     * NON si ritenta su un 4xx: e' il servizio a valle che rifiuta la richiesta, e ripeterla
     * darebbe lo stesso esito ritardando solo la risposta. La distinzione conta: ritentare
     * cio' che non puo' riuscire e' il modo per trasformare un errore chiaro in un timeout.
     *
     * I DELETE sono idempotenti, quindi un tentativo che era in realta' riuscito ma la cui
     * risposta si e' persa non fa danni al giro successivo.
     */
    private boolean delete(RestClient client, String uri, Long userId, String what) {
        Exception ultima = null;
        for (int attempt = 1; attempt <= TENTATIVI; attempt++) {
            try {
                client.delete()
                        .uri(uri, userId)
                        .header(HttpHeaders.AUTHORIZATION, currentAuthorization())
                        // Senza questa riga la catena di correlazione si spezza proprio qui:
                        // i servizi a valle non ricevono l'identificativo, se ne generano uno
                        // nuovo, e un'operazione che attraversa tre servizi finisce nei log
                        // sotto tre chiavi diverse. Cioe' la correlazione funzionerebbe
                        // ovunque tranne dove serve.
                        .header(RequestCorrelationFilter.INTESTAZIONE, RequestCorrelationFilter.current())
                        .retrieve()
                        .toBodilessEntity();
                if (attempt > 1) {
                    logger.info("{} dell'utenteId={} eliminate al tentativo {}", what, userId, attempt);
                }
                return true;
            } catch (HttpClientErrorException e) {
                logger.error("{} dell'utenteId={}: il servizio a valle ha rifiutato la richiesta "
                        + "({}). Non si ritenta: ripetere darebbe lo stesso esito.",
                        what, userId, e.getStatusCode());
                return false;
            } catch (Exception e) {
                ultima = e;
                if (attempt < TENTATIVI) {
                    attendi(ATTESA_INIZIALE_MS * attempt);
                }
            }
        }
        logger.error("{} dell'utenteId={} non eliminate dopo {} tentativi: l'utente NON viene "
                + "rimosso, cosi' quelle righe hanno ancora un proprietario e l'operazione "
                + "resta ripetibile. Causa: {}",
                what, userId, TENTATIVI, ultima != null ? ultima.getMessage() : "sconosciuta");
        return false;
    }

    private void attendi(long millisecondi) {
        try {
            Thread.sleep(millisecondi);
        } catch (InterruptedException e) {
            // Rimettere il flag e smettere di ritentare: chi ha interrotto il thread vuole
            // che si fermi, non che dorma di nuovo.
            Thread.currentThread().interrupt();
        }
    }

    private String currentAuthorization() {
        String header = currentRequest.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null ? header : "";
    }
}
