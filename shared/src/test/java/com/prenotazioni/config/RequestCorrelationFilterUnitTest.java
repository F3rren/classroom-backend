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
class RequestCorrelationFilterUnitTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

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
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.INTESTAZIONE, "REQ_DALGATEWAY");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(RequestCorrelationFilter.ATTRIBUTO)).isEqualTo("REQ_DALGATEWAY");
        assertThat(response.getHeader(RequestCorrelationFilter.INTESTAZIONE)).isEqualTo("REQ_DALGATEWAY");
    }

    @Test
    void neGeneraUnoQuandoLIntestazioneManca() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTO);
        assertThat(id).isNotBlank();
        // Rimandarlo indietro serve a chi apre una segnalazione: puo' citare l'id anche
        // quando la risposta e' un 204 o un corpo che non lo contiene.
        assertThat(response.getHeader(RequestCorrelationFilter.INTESTAZIONE)).isEqualTo(id);
    }

    @Test
    void controllerEGestoreDegliErroriLeggonoLoStessoValore() throws Exception {
        // La regressione che questa classe e' nata per chiudere: due chiamate a corrente()
        // dentro la stessa richiesta devono dare lo stesso valore. Prima erano due
        // generateSessionId() diversi e una richiesta fallita compariva nei log due volte,
        // sotto due chiavi, senza modo di collegarle.
        MockHttpServletRequest request = new MockHttpServletRequest();
        String[] letture = new String[2];

        FilterChain dentroLaRichiesta = (req, res) -> {
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes((MockHttpServletRequest) req));
            letture[0] = RequestCorrelationFilter.current();   // il controller
            letture[1] = RequestCorrelationFilter.current();   // il gestore degli errori
        };

        filter.doFilter(request, new MockHttpServletResponse(), dentroLaRichiesta);

        assertThat(letture[0]).isNotBlank().isEqualTo(letture[1]);
    }

    @Test
    void ripiegaFuoriDaUnaRichiestaHttp() {
        // Un consumatore di messaggi o un'attivita' pianificata non ha una richiesta.
        // Meglio un identificativo scollegato che un null che finisce nella risposta
        // stampato come la stringa "null".
        assertThat(RequestCorrelationFilter.current()).isNotBlank();
    }

    @Test
    void applicaAMdcUsaLIdentificativoRicevuto() {
        // La meta' della catena che non passa da HTTP: un ascoltatore AMQP gira su un
        // thread suo, fuori da qualunque richiesta, e senza questo la notifica creata da
        // un evento comparirebbe nei log scollegata dalla cancellazione che l'ha causata.
        RequestCorrelationFilter.applyToMdc("REQ_DALLEVENTO");

        assertThat(MDC.get("requestId")).isEqualTo("REQ_DALLEVENTO");
    }

    @Test
    void applicaAMdcRipiegaSuUnoGeneratoSeManca() {
        // Un messaggio pubblicato prima che l'intestazione esistesse deve continuare a
        // essere consumato: assente non e' un errore, e "null" nei log sarebbe peggio.
        RequestCorrelationFilter.applyToMdc(null);
        assertThat(MDC.get("requestId")).isNotBlank().isNotEqualTo("null");

        RequestCorrelationFilter.applyToMdc("   ");
        assertThat(MDC.get("requestId")).isNotBlank().doesNotContain(" ");
    }

    @Test
    void svuotaMdcTogliLIdentificativo() {
        RequestCorrelationFilter.applyToMdc("REQ_QUALCOSA");
        RequestCorrelationFilter.clearMdc();

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void svuotaMdcAlTermine() throws Exception {
        // I thread vengono riusati: un MDC non ripulito farebbe comparire l'identificativo
        // di questa richiesta nei log di quella successiva, che e' peggio di non averlo.
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("requestId")).isNull();
    }
}
