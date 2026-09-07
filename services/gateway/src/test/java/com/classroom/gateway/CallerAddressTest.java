package com.classroom.gateway;

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
 * Which address reaches the services as the caller's address.
 *
 * This is not a configuration detail: auth-service rate-limits login attempts on a key that
 * starts with that address, and the three services declare
 * server.forward-headers-strategy=framework, meaning they TRUST X-Forwarded-For to derive
 * it. If the caller can choose the value that arrives, the attempt limit is sidestepped by
 * changing it on every request.
 *
 * Spring Cloud Gateway's default behaviour is to APPEND to an existing X-Forwarded-For
 * rather than replace it, and ForwardedHeaderFilter reads the first value: without
 * spring.cloud.gateway.x-forwarded.for-append=false these tests fail, and that is how they
 * were written - before the fix, to watch the bypass happen instead of assuming it was
 * closed.
 *
 * If one day there were a real proxy in front of the gateway, this choice would have to be
 * revisited: there the incoming header would be legitimate, and the right answer would be
 * to trust the proxy - not the client.
 */
@SpringBootTest
class CallerAddressTest {

    private static final String HEADER = "X-Forwarded-For";

    @Autowired
    private XForwardedHeadersFilter filter;

    /** The headers the gateway would send on to the downstream service. */
    private HttpHeaders forwarded(String declaredByClient, String realAddress) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
                .get("/api/auth/login")
                .remoteAddress(new InetSocketAddress(realAddress, 51234));
        if (declaredByClient != null) {
            builder.header(HEADER, declaredByClient);
        }
        MockServerHttpRequest request = builder.build();
        return filter.filter(request.getHeaders(), MockServerWebExchange.from(request));
    }

    @Test
    void anAddressDeclaredByTheClientNeverReachesTheServices() {
        // THE test. If this falls, the login attempt limit is bypassed by sending a
        // different X-Forwarded-For on every request, and no other check notices.
        HttpHeaders forwarded = forwarded("9.9.9.9", "203.0.113.7");

        assertThat(forwarded.get(HEADER))
                .as("the gateway must write only the address of its direct peer")
                .containsExactly("203.0.113.7");
    }

    @Test
    void notEvenAnInventedChainSurvives() {
        // Someone bypassing the limit does not send a single address: they send a chain,
        // hoping the first value wins. The same rule applies.
        HttpHeaders forwarded = forwarded("9.9.9.9, 8.8.8.8, 7.7.7.7", "203.0.113.7");

        assertThat(forwarded.get(HEADER)).containsExactly("203.0.113.7");
    }

    @Test
    void theRealAddressTravelsAnyway() {
        // The other half of the requirement: discarding the declared one must not mean
        // sending none at all. With no header the services would all see the same address -
        // the gateway's - and anybody could exhaust the counter for somebody else's email
        // address and lock its owner out.
        HttpHeaders forwarded = forwarded(null, "198.51.100.42");

        assertThat(forwarded.get(HEADER)).containsExactly("198.51.100.42");
    }

    @Test
    void differentCallersStayDistinct() {
        // If they collapsed onto the same value the limiter would count everyone together,
        // and a single attacker would be enough to block everybody else's login.
        assertThat(forwarded(null, "203.0.113.7").getFirst(HEADER))
                .isNotEqualTo(forwarded(null, "198.51.100.42").getFirst(HEADER));
    }

    @Test
    void theConfigurationHoldingAllThisUpIsExplicit() {
        // Redundant with the tests above, and kept on purpose: if an upgrade changed the
        // default of for-append, this says in one line WHICH configuration line to put
        // back, instead of leaving four red assertions to interpret.
        assertThat(filter.isForAppend())
                .as("spring.cloud.gateway.x-forwarded.for-append must stay false")
                .isFalse();
    }
}
