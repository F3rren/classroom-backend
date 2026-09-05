package com.prenotazioni.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * L'identificativo di richiesta coniato al bordo.
 *
 * Serve a una cosa sola: permettere di seguire una chiamata attraverso piu' servizi. I
 * servizi a valle riusano X-Request-Id se la ricevono, ma finche' nessuno la manda ognuno
 * se ne genera una propria e una chiamata che attraversa gateway e servizio prenotazioni
 * resta spezzata in due tronconi che nessuno puo' ricollegare.
 *
 * I due casi limite qui sotto sono entrambi difetti trovati provando il gateway dal vivo,
 * non ipotesi: nei test unitari del filtro non comparivano.
 *
 * Come in RisposteErroreTest, la rotta punta a una porta dove non ascolta nessuno: serve un
 * servizio a valle irraggiungibile senza spegnerne uno vero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.gateway.routes[0].id=verso-il-nulla",
        "spring.cloud.gateway.routes[0].uri=http://localhost:9",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/rooms/**"
})
class EdgeCorrelationFilterTest {

    @Autowired
    private WebTestClient client;

    @Test
    void coniaUnIdentificativoQuandoIlChiamanteNonNeManda() {
        client.get().uri("/api/rooms")
                .exchange()
                .expectHeader().value(EdgeCorrelationFilter.INTESTAZIONE, id ->
                        org.assertj.core.api.Assertions.assertThat(id).startsWith("REQ_"))
                .expectBody()
                // Lo stesso valore nel corpo: chi apre una segnalazione cita un id solo, e
                // quell'id e' cercabile nei log di tutti i servizi coinvolti.
                .jsonPath("$.sessionId").value(org.hamcrest.Matchers.startsWith("REQ_"));
    }

    @Test
    void rispettaLIdentificativoRicevuto() {
        // Il punto dell'intero meccanismo: se un giorno davanti al gateway ci fosse un proxy
        // o un frontend che gia' traccia le chiamate, sovrascrivere il suo id romperebbe
        // proprio la catena che questo filtro esiste per tenere insieme.
        client.get().uri("/api/rooms")
                .header(EdgeCorrelationFilter.INTESTAZIONE, "REQ_DALCHIAMANTE")
                .exchange()
                .expectHeader().valueEquals(EdgeCorrelationFilter.INTESTAZIONE, "REQ_DALCHIAMANTE")
                .expectBody()
                .jsonPath("$.sessionId").isEqualTo("REQ_DALCHIAMANTE");
    }

    @Test
    void sopravviveAUnPercorsoSenzaRotta() {
        // Trovato dal vivo: su un percorso che non corrisponde a nessuna rotta il 404 nasce
        // nella mappatura, PRIMA che la catena dei GlobalFilter parta. Il filtro non gira e
        // senza il ripiego in GatewayErrorHandler l'id del chiamante andava perso proprio
        // sulla richiesta piu' sospetta - quella verso un percorso che non esiste.
        client.get().uri("/percorso/che/non/esiste")
                .header(EdgeCorrelationFilter.INTESTAZIONE, "REQ_SENZAROTTA")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals(EdgeCorrelationFilter.INTESTAZIONE, "REQ_SENZAROTTA")
                .expectBody()
                .jsonPath("$.sessionId").isEqualTo("REQ_SENZAROTTA");
    }

    @Test
    void nonDuplicaLIntestazioneQuandoLaRimandaAncheIlServizioAValle() {
        // Trovato dal vivo: scrivere l'intestazione prima di inoltrare non basta, perche' il
        // gateway UNISCE le intestazioni della risposta a valle alle proprie e il client se
        // la ritrovava due volte. Il rimedio e' scriverla in beforeCommit, dopo la fusione.
        client.get().uri("/api/rooms")
                .header(EdgeCorrelationFilter.INTESTAZIONE, "REQ_UNAVOLTASOLA")
                .exchange()
                .expectHeader().values(EdgeCorrelationFilter.INTESTAZIONE, valori ->
                        org.assertj.core.api.Assertions.assertThat(valori)
                                .containsExactly("REQ_UNAVOLTASOLA"));
    }
}
