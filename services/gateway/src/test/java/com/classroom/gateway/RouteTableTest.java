package com.classroom.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.lang.NonNull;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The REAL route table, the one in application.yml.
 *
 * It is needed because RoutingTest, which looks like it covers routing, actually declares
 * its own routes in @TestPropertySource: it checks the gateway's mechanism against a
 * synthetic table, not against the one that runs in production. A mistake in the real table
 * would pass straight through it unnoticed.
 *
 * What has to be held still is above all AN ORDER. Two different services expose
 * /api/admin: auth-service under /api/admin/users, booking-service everything else. Spring
 * Cloud Gateway evaluates routes in declaration order, so the more specific one has to come
 * first. If somebody reordered them, /api/admin/users would end up at booking-service and
 * answer 404 - with no configuration error, no log, and nothing to suggest why.
 */
@SpringBootTest
class RouteTableTest {

    @Autowired
    private RouteLocator routes;

    /** The id of the first route that accepts the path, as the gateway would pick it. */
    private String firstRouteMatching(@NonNull String path) {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        List<Route> ordered = routes.getRoutes().collectList().block();
        assertThat(ordered).as("no route loaded: application.yml was not read").isNotEmpty();
        for (Route r : ordered) {
            if (Boolean.TRUE.equals(Mono.from(r.getPredicate().apply(exchange)).block())) {
                return r.getId();
            }
        }
        return null;
    }

    /** The address a route sends to, used to tell the downstream services apart. */
    private String destinationOf(String routeId) {
        return routes.getRoutes()
                .filter(r -> r.getId().equals(routeId))
                .map(r -> r.getUri().toString())
                .blockFirst();
    }

    @Test
    void theAdminUserPathsGoToTheUserService() {
        // THE regression to keep closed: this route is declared BEFORE the generic one on
        // /api/admin/**, and it is the order that makes it win.
        assertThat(firstRouteMatching("/api/admin/users")).isEqualTo("authentication");
        assertThat(firstRouteMatching("/api/admin/users/42")).isEqualTo("authentication");
    }

    @Test
    void theRestOfAdminGoesToTheBookingService() {
        assertThat(firstRouteMatching("/api/admin/rooms")).isEqualTo("application");
        assertThat(firstRouteMatching("/api/admin/bookings")).isEqualTo("application");
    }

    @Test
    void onlyTheOrderDecidesWhoReceivesTheAdminUserPaths() {
        // Without this, the two tests above could pass by construction: if /api/admin/users
        // matched a single route, the order would not matter and there would be nothing to
        // hold still. Here BOTH are required to accept it, so the only thing sending the
        // request to the right service is the position.
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users").build());
        List<String> whoAcceptsIt = routes.getRoutes()
                .filter(r -> Boolean.TRUE.equals(Mono.from(r.getPredicate().apply(exchange)).block()))
                .map(r -> r.getId())
                .collectList().block();

        assertThat(whoAcceptsIt).containsExactly("authentication", "application");
    }

    @Test
    void theTwoAdminRoutesPointToDifferentServices() {
        // If they pointed at the same one the order would not matter and these tests would
        // prove nothing: this is what makes the two above meaningful.
        assertThat(destinationOf("authentication")).isNotEqualTo(destinationOf("application"));
    }

    @Test
    void theInternalRoutesStayOutOfReach() {
        // They are called by other services, not by the browser: exposing them would let
        // anybody holding an admin token fabricate arbitrary notifications.
        assertThat(firstRouteMatching("/api/notifications/internal/user/1")).isEqualTo("notifications-internal-blocked");
        assertThat(firstRouteMatching("/api/bookings/internal/user/1")).isEqualTo("bookings-internal-blocked");
    }

    @Test
    @SuppressWarnings("null")
    void everyPublicPathFindsARoute() {
        // A path with no route does not produce a configuration error: it produces a 404 for
        // the caller, and that is how a new endpoint stays invisible after being written and
        // shipped.
        for (String path : new String[]{
                "/api/auth/login", "/api/me", "/api/rooms", "/api/bookings",
                "/api/notifications", "/api/admin/users", "/api/admin/rooms"}) {
            assertThat(firstRouteMatching(path))
                    .as("no route for %s", path)
                    .isNotNull();
        }
    }

    @Test
    void anInventedPathFindsNoRoute() {
        assertThat(firstRouteMatching("/path/that/does/not/exist")).isNull();
    }
}
