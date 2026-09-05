package com.prenotazioni.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * La forma delle risposte d'errore del gateway.
 *
 * E' un test di contratto, non di comportamento: il frontend legge queste chiavi esatte, e
 * fino a poco fa il gateway ne restituiva altre. Un client che leggeva userMessage otteneva
 * undefined ogni volta che a fallire era il gateway - cioe' quando un servizio e' giu', che
 * e' quando un messaggio sensato serve di piu'.
 *
 * L'insieme delle chiavi e' volutamente bloccato con jsonPath su ciascuna: l'envelope e'
 * ricostruito a mano qui dentro (il gateway non puo' dipendere da shared senza trascinarsi
 * Tomcat) e questo test e' cio' che tiene le due forme allineate. Il gemello dall'altra
 * parte e' ApiEnvelopeUnitTest in shared.
 *
 * Le rotte puntano a una porta dove non ascolta nessuno: e' cosi' che si ottiene un
 * servizio a valle irraggiungibile senza doverne spegnere uno vero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.gateway.routes[0].id=verso-il-nulla",
        "spring.cloud.gateway.routes[0].uri=http://localhost:9",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/rooms/**"
})
class ErrorResponsesGatewayTest {

    @Autowired
    private WebTestClient client;

    @Test
    void unServizioIrraggiungibileDa503ENonPiu500() {
        // 503 e non 500: il servizio non risponde, ma il problema e' temporaneo e riprovare
        // ha senso. Prima erano indistinguibili, ed entrambi 500.
        client.get().uri("/api/rooms")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.userMessage").exists()
                .jsonPath("$.sessionId").exists();
    }

    @Test
    void unPercorsoSconosciutoDa404NellaFormaGiusta() {
        client.get().uri("/api/inventato")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error").isEqualTo("NOT_FOUND")
                .jsonPath("$.userMessage").isEqualTo("La risorsa richiesta non esiste.");
    }

    @Test
    void laRispostaHaEsattamenteLeChiaviDellEnvelopeDeiServizi() {
        // Il vincolo vero di questo file. Le chiavi sono sette in ApiEnvelope, ma "data"
        // e' omesso quando nullo (@JsonInclude NON_NULL), quindi un errore ne espone sei.
        client.get().uri("/api/inventato")
                .exchange()
                .expectBody()
                .jsonPath("$.success").exists()
                .jsonPath("$.error").exists()
                .jsonPath("$.message").exists()
                .jsonPath("$.userMessage").exists()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.sessionId").exists()
                // e nessuna delle chiavi del formato predefinito di Spring, che erano
                // il problema da cui e' nata questa classe
                .jsonPath("$.path").doesNotExist()
                .jsonPath("$.status").doesNotExist()
                .jsonPath("$.requestId").doesNotExist();
    }

    @Test
    void ilTimestampUsaIlFormatoDeiServiziENonLIsoDiSpring() {
        // yyyy-MM-dd HH:mm:ss, lo stesso di util.Timestamps. Prima era ISO con offset,
        // quindi due formati diversi nella stessa API a seconda di chi rispondeva.
        client.get().uri("/api/inventato")
                .exchange()
                .expectBody()
                .jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void ilMessaggioPerLUtenteNonEsponeDettagliInterni() {
        // Nessun nome di classe, nessun indirizzo, nessuno stack: cio' che serve a chi
        // indaga sta nei log insieme al sessionId, non nella risposta.
        client.get().uri("/api/rooms")
                .exchange()
                .expectBody()
                .jsonPath("$.userMessage").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("localhost")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("java."))));
    }
}
