package com.prenotazioni.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il filtro esiste per una ragione sola: dare a una richiesta UN identificativo, non uno
 * per ogni classe che la tocca. I test qui sotto fissano i tre comportamenti da cui
 * dipende quella garanzia; se uno si rompe l'identificativo torna a essere decorativo.
 */
class CorrelazioneRichiestaUnitTest {

    private final CorrelazioneRichiesta filtro = new CorrelazioneRichiesta();

    @AfterEach
    void pulisci() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void riusaLIdentificativoRicevutoDaChiamaChiama() throws Exception {
        // Questo e' il punto dell'intera classe in un sistema a piu' servizi: il gateway
        // genera l'id, i servizi a valle lo ereditano. Se qui se ne generasse uno nuovo,
        // un giro fra gateway e servizio prenotazioni resterebbe impossibile da ricucire.
        MockHttpServletRequest richiesta = new MockHttpServletRequest();
        richiesta.addHeader(CorrelazioneRichiesta.INTESTAZIONE, "REQ_DALGATEWAY");
        MockHttpServletResponse risposta = new MockHttpServletResponse();

        filtro.doFilter(richiesta, risposta, new MockFilterChain());

        assertThat(richiesta.getAttribute(CorrelazioneRichiesta.ATTRIBUTO)).isEqualTo("REQ_DALGATEWAY");
        assertThat(risposta.getHeader(CorrelazioneRichiesta.INTESTAZIONE)).isEqualTo("REQ_DALGATEWAY");
    }

    @Test
    void neGeneraUnoQuandoLIntestazioneManca() throws Exception {
        MockHttpServletRequest richiesta = new MockHttpServletRequest();
        MockHttpServletResponse risposta = new MockHttpServletResponse();

        filtro.doFilter(richiesta, risposta, new MockFilterChain());

        String id = (String) richiesta.getAttribute(CorrelazioneRichiesta.ATTRIBUTO);
        assertThat(id).isNotBlank();
        // Rimandarlo indietro serve a chi apre una segnalazione: puo' citare l'id anche
        // quando la risposta e' un 204 o un corpo che non lo contiene.
        assertThat(risposta.getHeader(CorrelazioneRichiesta.INTESTAZIONE)).isEqualTo(id);
    }

    @Test
    void controllerEGestoreDegliErroriLeggonoLoStessoValore() throws Exception {
        // La regressione che questa classe e' nata per chiudere: due chiamate a corrente()
        // dentro la stessa richiesta devono dare lo stesso valore. Prima erano due
        // generateSessionId() diversi e una richiesta fallita compariva nei log due volte,
        // sotto due chiavi, senza modo di collegarle.
        MockHttpServletRequest richiesta = new MockHttpServletRequest();
        String[] letture = new String[2];

        FilterChain dentroLaRichiesta = (req, res) -> {
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes((MockHttpServletRequest) req));
            letture[0] = CorrelazioneRichiesta.corrente();   // il controller
            letture[1] = CorrelazioneRichiesta.corrente();   // il gestore degli errori
        };

        filtro.doFilter(richiesta, new MockHttpServletResponse(), dentroLaRichiesta);

        assertThat(letture[0]).isNotBlank().isEqualTo(letture[1]);
    }

    @Test
    void ripiegaFuoriDaUnaRichiestaHttp() {
        // Un consumatore di messaggi o un'attivita' pianificata non ha una richiesta.
        // Meglio un identificativo scollegato che un null che finisce nella risposta
        // stampato come la stringa "null".
        assertThat(CorrelazioneRichiesta.corrente()).isNotBlank();
    }

    @Test
    void svuotaMdcAlTermine() throws Exception {
        // I thread vengono riusati: un MDC non ripulito farebbe comparire l'identificativo
        // di questa richiesta nei log di quella successiva, che e' peggio di non averlo.
        filtro.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("requestId")).isNull();
    }
}
