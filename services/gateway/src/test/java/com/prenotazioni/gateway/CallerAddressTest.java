package com.prenotazioni.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quale indirizzo arriva ai servizi come indirizzo del chiamante.
 *
 * Non e' un dettaglio di configurazione: auth-service limita i tentativi di login su una
 * chiave che comincia con quell'indirizzo, e i tre servizi dichiarano
 * server.forward-headers-strategy=framework, cioe' si FIDANO di X-Forwarded-For per
 * ricavarlo. Se il valore che arriva lo puo' scegliere chi chiama, il limite sui tentativi
 * si evita cambiandolo a ogni richiesta.
 *
 * Il comportamento predefinito di Spring Cloud Gateway e' AGGIUNGERE in coda a un
 * X-Forwarded-For gia' presente invece di sostituirlo, e ForwardedHeaderFilter legge il
 * primo valore: senza spring.cloud.gateway.x-forwarded.for-append=false questi test
 * falliscono, ed e' cosi' che sono stati scritti - prima della correzione, per vedere
 * l'aggiramento accadere invece di dare per buono che fosse chiuso.
 *
 * Se un giorno davanti al gateway ci fosse un proxy vero, questa scelta andrebbe rifatta:
 * li' l'intestazione in arrivo sarebbe legittima, e la strada giusta sarebbe fidarsi del
 * proxy - non del client.
 */
@SpringBootTest
class CallerAddressTest {

    private static final String INTESTAZIONE = "X-Forwarded-For";

    @Autowired
    private XForwardedHeadersFilter filter;

    /** Le intestazioni che il gateway manderebbe al servizio a valle. */
    private HttpHeaders inoltrate(String dichiaratoDalClient, String indirizzoReale) {
        MockServerHttpRequest.BaseBuilder<?> costruttore = MockServerHttpRequest
                .get("/api/auth/login")
                .remoteAddress(new InetSocketAddress(indirizzoReale, 51234));
        if (dichiaratoDalClient != null) {
            costruttore.header(INTESTAZIONE, dichiaratoDalClient);
        }
        MockServerHttpRequest request = costruttore.build();
        return filter.filter(request.getHeaders(), MockServerWebExchange.from(request));
    }

    @Test
    void unIndirizzoDichiaratoDalClientNonArrivaAiServizi() {
        // LA prova. Se questo cade, il limite sui tentativi di login si aggira mandando un
        // X-Forwarded-For diverso a ogni richiesta, e nessun altro controllo se ne accorge.
        HttpHeaders inoltrate = inoltrate("9.9.9.9", "203.0.113.7");

        assertThat(inoltrate.get(INTESTAZIONE))
                .as("il gateway deve scrivere solo l'indirizzo del suo interlocutore diretto")
                .containsExactly("203.0.113.7");
    }

    @Test
    void nemmenoUnaCatenaInventataSopravvive() {
        // Chi vuole aggirare il limite non manda un indirizzo solo: ne manda una catena,
        // sperando che il primo valore vinca. Vale la stessa regola.
        HttpHeaders inoltrate = inoltrate("9.9.9.9, 8.8.8.8, 7.7.7.7", "203.0.113.7");

        assertThat(inoltrate.get(INTESTAZIONE)).containsExactly("203.0.113.7");
    }

    @Test
    void lIndirizzoRealeViaggiaComunque() {
        // L'altra meta' del requisito: scartare quello dichiarato non deve voler dire non
        // mandarne nessuno. Senza intestazione i servizi vedrebbero tutti lo stesso
        // indirizzo - quello del gateway - e chiunque potrebbe esaurire il contatore di un
        // indirizzo email altrui tenendone fuori il proprietario.
        HttpHeaders inoltrate = inoltrate(null, "198.51.100.42");

        assertThat(inoltrate.get(INTESTAZIONE)).containsExactly("198.51.100.42");
    }

    @Test
    void chiamantiDiversiRestanoDistinti() {
        // Se collassassero sullo stesso valore, il limitatore conterebbe tutti insieme e
        // un solo attaccante basterebbe a bloccare il login di chiunque altro.
        assertThat(inoltrate(null, "203.0.113.7").getFirst(INTESTAZIONE))
                .isNotEqualTo(inoltrate(null, "198.51.100.42").getFirst(INTESTAZIONE));
    }

    @Test
    void laConfigurazioneCheReggeTuttoQuestoEEsplicita() {
        // Ridondante rispetto ai test sopra, e tenuto apposta: se un aggiornamento cambiasse
        // il default di for-append, questo dice in una riga QUALE riga di configurazione
        // rimettere, invece di lasciare quattro asserzioni rosse da interpretare.
        assertThat(filter.isForAppend())
                .as("spring.cloud.gateway.x-forwarded.for-append deve restare false")
                .isFalse();
    }
}
