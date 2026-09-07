package com.classroom.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The request id minted at the edge.
 *
 * It serves one purpose: making a call followable across several services. The downstream
 * services reuse X-Request-Id when they receive one, but as long as nobody sends it each
 * generates its own, and a call crossing the gateway and the booking service stays split
 * into two halves nobody can put back together.
 *
 * The two edge cases below are both defects found by exercising the gateway for real, not
 * hypotheses: neither showed up in the filter's unit tests.
 *
 * As in ErrorResponsesGatewayTest, the route points at a port where nothing is listening:
 * that is how you get an unreachable downstream service without shutting a real one down.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.gateway.routes[0].id=to-nowhere",
        "spring.cloud.gateway.routes[0].uri=http://localhost:9",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/rooms/**"
})
class EdgeCorrelationFilterTest {

    @Autowired
    private WebTestClient client;

    @Test
    void mintsAnIdWhenTheCallerSendsNone() {
        client.get().uri("/api/rooms")
                .exchange()
                .expectHeader().value(EdgeCorrelationFilter.HEADER, id ->
                        org.assertj.core.api.Assertions.assertThat(id).startsWith("REQ_"))
                .expectBody()
                // The same value in the body: whoever opens a report quotes a single id, and
                // that id is searchable in the logs of every service involved.
                .jsonPath("$.sessionId").value(org.hamcrest.Matchers.startsWith("REQ_"));
    }

    @Test
    void keepsTheIdItReceived() {
        // The point of the whole mechanism: if one day there were a proxy or a frontend in
        // front of the gateway that already traces calls, overwriting its id would break
        // exactly the chain this filter exists to hold together.
        client.get().uri("/api/rooms")
                .header(EdgeCorrelationFilter.HEADER, "REQ_FROMCALLER")
                .exchange()
                .expectHeader().valueEquals(EdgeCorrelationFilter.HEADER, "REQ_FROMCALLER")
                .expectBody()
                .jsonPath("$.sessionId").isEqualTo("REQ_FROMCALLER");
    }

    @Test
    void survivesAPathWithNoRoute() {
        // Found for real: on a path matching no route the 404 is born in the mapping, BEFORE
        // the GlobalFilter chain starts. The filter does not run, and without the fallback in
        // GatewayErrorHandler the caller's id was lost on exactly the most suspicious request
        // there is - one aimed at a path that does not exist.
        client.get().uri("/path/that/does/not/exist")
                .header(EdgeCorrelationFilter.HEADER, "REQ_NOROUTE")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals(EdgeCorrelationFilter.HEADER, "REQ_NOROUTE")
                .expectBody()
                .jsonPath("$.sessionId").isEqualTo("REQ_NOROUTE");
    }

    @Test
    void doesNotDuplicateTheHeaderWhenTheDownstreamServiceEchoesItBack() {
        // Found for real: writing the header before forwarding is not enough, because the
        // gateway MERGES the downstream response headers with its own and the client got it
        // twice. The remedy is to write it in beforeCommit, after that merge.
        client.get().uri("/api/rooms")
                .header(EdgeCorrelationFilter.HEADER, "REQ_ONLYONCE")
                .exchange()
                .expectHeader().values(EdgeCorrelationFilter.HEADER, values ->
                        org.assertj.core.api.Assertions.assertThat(values)
                                .containsExactly("REQ_ONLYONCE"));
    }
}
