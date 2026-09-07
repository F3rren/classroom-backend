package com.prenotazioni.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Checks the routing DECISIONS, not the downstream services.
 *
 * The routes point at a port where nothing is listening, and that alone makes the two
 * answers distinguishable without starting any service or even an HTTP stub:
 *
 *  - a routed path tries to reach the service and fails with a 5xx;
 *  - a blocked or unknown path answers 404 without ever leaving the gateway.
 *
 * What is checked is therefore exactly the gateway's choice, which is its only
 * responsibility.
 *
 * The route ids here are the ones this test declares for itself, not the ones in
 * application.yml: the real table is covered by RouteTableTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.gateway.routes[0].id=notifications-internal-blocked",
        "spring.cloud.gateway.routes[0].uri=forward:/route-not-exposed",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/notifications/internal/**",
        "spring.cloud.gateway.routes[1].id=notifications",
        "spring.cloud.gateway.routes[1].uri=http://localhost:9",
        "spring.cloud.gateway.routes[1].predicates[0]=Path=/api/notifications/**",
        "spring.cloud.gateway.routes[2].id=application",
        "spring.cloud.gateway.routes[2].uri=http://localhost:9",
        "spring.cloud.gateway.routes[2].predicates[0]=Path=/api/auth/**,/api/me/**,/api/rooms/**,/api/bookings/**,/api/admin/**"
})
class RoutingTest {

    @Autowired
    private WebTestClient client;

    @Test
    void theInternalRoutesAreNotReachableFromOutside() {
        // 404 and not 5xx: the request never even set off towards notification-service.
        // This is the most important check in the file: those routes create arbitrary
        // notifications and must stay a conversation between services.
        client.post().uri("/api/notifications/internal/booking-cancellation")
                .exchange()
                .expectStatus().isNotFound();

        client.delete().uri("/api/notifications/internal/user/1")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void thePublicNotificationPathsAreRouted() {
        // 5xx: the gateway decided to forward and found nobody listening. That is the
        // proof that the route was recognised.
        client.get().uri("/api/notifications")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void theMainApplicationRoutesAreRouted() {
        for (String path : new String[]{"/api/rooms", "/api/bookings", "/api/me", "/api/admin/users"}) {
            client.get().uri(path)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    @Test
    void loginIsRoutedLikeTheRest() {
        // A public route, but not a special case for the gateway: it validates no tokens,
        // so it has no reason to tell protected routes from open ones.
        client.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void anUnknownPathStaysOut() {
        client.get().uri("/api/made-up")
                .exchange()
                .expectStatus().isNotFound();
    }
}
