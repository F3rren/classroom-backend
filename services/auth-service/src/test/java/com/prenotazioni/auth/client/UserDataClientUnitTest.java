package com.prenotazioni.auth.client;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Le chiamate che cancellano i dati dell'utente negli altri servizi.
 *
 * Il punto di questi test e' contare le richieste che partono davvero, non solo l'esito: la
 * differenza fra "ritenta" e "non ritenta" e' invisibile guardando il valore di ritorno, ed
 * e' esattamente cio' che decide se un guasto passeggero lascia l'operazione a meta'.
 *
 * La distinzione da tenere ferma: si ritenta su un guasto di trasporto o un 5xx, perche' al
 * secondo colpo spesso funziona; NON si ritenta su un 4xx, perche' e' il servizio a valle che
 * rifiuta e ripetere darebbe lo stesso esito ritardando solo la risposta.
 */
class UserDataClientUnitTest {

    private static final String NOTIFICHE = "http://notifiche.test";
    private static final String PRENOTAZIONI = "http://prenotazioni.test";
    private static final String URI_NOTIFICHE = NOTIFICHE + "/api/notifications/internal/user/7";
    private static final String URI_PRENOTAZIONI = PRENOTAZIONI + "/api/bookings/internal/user/7";

    private RestClient.Builder costruttore;
    private MockRestServiceServer servizioFinto;
    private UserDataClient client;

    @BeforeEach
    void setUp() {
        costruttore = RestClient.builder();
        servizioFinto = MockRestServiceServer.bindTo(costruttore).build();
        // La richiesta corrente serve solo a inoltrare l'intestazione Authorization: qui non
        // c'e' una richiesta HTTP in corso, e un mock che risponde null va benissimo.
        client = new UserDataClient(costruttore, NOTIFICHE, PRENOTAZIONI, mock(HttpServletRequest.class));
    }

    @Test
    void quandoVaTuttoBeneNonRestaNienteIndietro() {
        servizioFinto.expect(requestTo(URI_NOTIFICHE)).andRespond(withSuccess());
        servizioFinto.expect(requestTo(URI_PRENOTAZIONI)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).isEmpty();
        servizioFinto.verify();
    }

    @Test
    void unGuastoPasseggeroVieneSuperatoRitentando() {
        // LA ragione dei ritentativi. Un servizio che sta riavviando fallisce il primo colpo
        // e risponde al secondo: prima bastava questo a lasciare la cancellazione a meta',
        // e a farla concludere doveva essere una persona che se ne accorgeva.
        servizioFinto.expect(requestTo(URI_NOTIFICHE)).andRespond(withServerError());
        servizioFinto.expect(requestTo(URI_NOTIFICHE)).andRespond(withSuccess());
        servizioFinto.expect(requestTo(URI_PRENOTAZIONI)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).isEmpty();
        servizioFinto.verify();
    }

    @Test
    void dopoTreTentativiSiArrende() {
        // I ritentativi non sono infiniti: un servizio davvero giu' non deve tenere appesa
        // la richiesta dell'amministratore.
        servizioFinto.expect(ExpectedCount.times(3), requestTo(URI_NOTIFICHE)).andRespond(withServerError());
        servizioFinto.expect(requestTo(URI_PRENOTAZIONI)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).containsExactly("notifications");
        servizioFinto.verify();
    }

    @Test
    void unRifiutoDelServizioAValleNonSiRitenta() {
        // Un 4xx e' una risposta, non un guasto: ripeterla darebbe lo stesso esito. Il
        // conteggio esatto e' l'unica cosa che distingue questo caso dal precedente -
        // l'esito e' identico, il comportamento no.
        servizioFinto.expect(ExpectedCount.once(), requestTo(URI_NOTIFICHE))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        servizioFinto.expect(requestTo(URI_PRENOTAZIONI)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).containsExactly("notifications");
        servizioFinto.verify();
    }

    @Test
    void ilSecondoServizioVieneTentatoAncheSeIlPrimoFallisce() {
        // Fermarsi al primo errore lascerebbe piu' roba indietro senza dire di piu' a chi
        // legge il messaggio: entrambi vanno tentati, e l'errore li nomina entrambi.
        servizioFinto.expect(ExpectedCount.once(), requestTo(URI_NOTIFICHE))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        servizioFinto.expect(ExpectedCount.once(), requestTo(URI_PRENOTAZIONI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        List<String> failed = client.deleteDataOf(7L);

        assertThat(failed).containsExactly("notifications", "bookings");
        servizioFinto.verify();
    }

    @Test
    void ogniChiamataPortaLIdentificativoDiCorrelazione() {
        // Senza, un'operazione che attraversa tre servizi finisce nei log sotto tre chiavi
        // diverse: la correlazione funzionerebbe ovunque tranne dove serve.
        servizioFinto.expect(requestTo(URI_NOTIFICHE))
                .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("X-Request-Id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
                .andRespond(withSuccess());
        servizioFinto.expect(requestTo(URI_PRENOTAZIONI)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).isEmpty();
        servizioFinto.verify();
    }
}
