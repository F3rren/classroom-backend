package com.prenotazioni.auth.client;

import com.prenotazioni.config.CorrelazioneRichiesta;
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
public class DatiUtenteClient {

    private static final Logger logger = LoggerFactory.getLogger(DatiUtenteClient.class);

    private final RestClient notifiche;
    private final RestClient prenotazioni;
    private final HttpServletRequest richiestaCorrente;

    DatiUtenteClient(RestClient.Builder builder,
                     @Value("${prenotazioni.notifica-service.url:http://localhost:17104}") String urlNotifiche,
                     @Value("${prenotazioni.prenotazione-service.url:http://localhost:17103}") String urlPrenotazioni,
                     HttpServletRequest richiestaCorrente) {
        this.notifiche = builder.clone().baseUrl(urlNotifiche).build();
        this.prenotazioni = builder.clone().baseUrl(urlPrenotazioni).build();
        this.richiestaCorrente = richiestaCorrente;
    }

    /** @return true se la cancellazione a valle e' riuscita del tutto. */
    public boolean eliminaDatiDi(Long utenteId) {
        boolean tutto = elimina(notifiche, "/api/notifiche/interne/utente/{id}", utenteId, "notifiche");
        tutto &= elimina(prenotazioni, "/api/prenotazioni/interne/utente/{id}", utenteId, "prenotazioni");
        return tutto;
    }

    private boolean elimina(RestClient client, String uri, Long utenteId, String cosa) {
        try {
            client.delete()
                    .uri(uri, utenteId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationCorrente())
                    // Senza questa riga la catena di correlazione si spezza proprio qui:
                    // i servizi a valle non ricevono l'identificativo, se ne generano uno
                    // nuovo, e un'operazione che attraversa tre servizi finisce nei log
                    // sotto tre chiavi diverse. Cioe' la correlazione funzionerebbe
                    // ovunque tranne dove serve.
                    .header(CorrelazioneRichiesta.INTESTAZIONE, CorrelazioneRichiesta.corrente())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            logger.error("{} dell'utenteId={} non eliminate: restano righe orfane, che la chiave "
                    + "esterna rendeva impossibili prima della separazione. Causa: {}",
                    cosa, utenteId, e.getMessage());
            return false;
        }
    }

    private String authorizationCorrente() {
        String header = richiestaCorrente.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null ? header : "";
    }
}
