package com.classroom.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The shape of the gateway's error responses.
 *
 * This is a contract test, not a behaviour test: the frontend reads these exact keys, and
 * until recently the gateway returned different ones. A client reading userMessage got
 * undefined every time the failure was the gateway's - that is, whenever a service is down,
 * which is when a sensible message matters most.
 *
 * The set of keys is deliberately pinned with a jsonPath on each: the envelope is rebuilt by
 * hand in the gateway (which cannot depend on shared without dragging Tomcat in) and this
 * test is what keeps the two shapes aligned. Its twin on the other side is
 * ApiEnvelopeUnitTest in shared.
 *
 * The routes point at a port where nothing is listening: that is how you get an unreachable
 * downstream service without having to shut a real one down.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.gateway.routes[0].id=to-nowhere",
        "spring.cloud.gateway.routes[0].uri=http://localhost:9",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/rooms/**"
})
class ErrorResponsesGatewayTest {

    @Autowired
    private WebTestClient client;

    @Test
    void anUnreachableServiceGives503AndNoLonger500() {
        // 503 and not 500: the service is not answering, but the problem is temporary and
        // retrying makes sense. They used to be indistinguishable, and both 500.
        client.get().uri("/api/rooms")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith("application/json")
                // What separates a 503 from a 500 is that repeating it is worth something,
                // and this header is the only part of that a client can act on.
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.userMessage").exists()
                .jsonPath("$.sessionId").exists();
    }

    @Test
    void anUnknownPathGives404InTheRightShape() {
        client.get().uri("/api/made-up")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error").isEqualTo("NOT_FOUND")
                // Italian on purpose: userMessage is the one field a person reads.
                .jsonPath("$.userMessage").isEqualTo("La risorsa richiesta non esiste.");
    }

    @Test
    void theResponseHasExactlyTheServiceEnvelopeKeys() {
        // The real constraint of this file. ApiEnvelope has seven keys, but "data" is omitted
        // when null (@JsonInclude NON_NULL), so an error exposes six.
        client.get().uri("/api/made-up")
                .exchange()
                .expectBody()
                .jsonPath("$.success").exists()
                .jsonPath("$.error").exists()
                .jsonPath("$.message").exists()
                .jsonPath("$.userMessage").exists()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.sessionId").exists()
                // and none of the keys of Spring's default format, which were the problem
                // this class was born from
                .jsonPath("$.path").doesNotExist()
                .jsonPath("$.status").doesNotExist()
                .jsonPath("$.requestId").doesNotExist();
    }

    @Test
    @SuppressWarnings("null")
    void theTimestampUsesTheServiceFormatAndNotSpringsIso() {
        // yyyy-MM-dd HH:mm:ss, the same as util.Timestamps. It used to be ISO with an offset,
        // so the same API carried two different formats depending on who answered.
        client.get().uri("/api/made-up")
                .exchange()
                .expectBody()
                .jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @SuppressWarnings("null")
    void theUserMessageExposesNoInternalDetail() {
        // No class name, no address, no stack: what the investigator needs is in the logs
        // alongside the sessionId, not in the response.
        client.get().uri("/api/rooms")
                .exchange()
                .expectBody()
                .jsonPath("$.userMessage").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("localhost")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("java."))));
    }
}
