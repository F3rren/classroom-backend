package com.prenotazioni.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * The request id is born here, at the edge, and not inside each service.
 *
 * The downstream services already have a filter that reuses the X-Request-Id header when
 * they find one (RequestCorrelationFilter, in the shared module) - but as long as nobody
 * sends it, every service generates its own, and a call crossing the gateway and the
 * booking service stays split into two disconnected halves in the logs. Minting it here is
 * what makes that reuse worth anything: from this point on everyone is talking about the
 * same request.
 *
 * The filter sits as close to the entrance as possible (HIGHEST_PRECEDENCE) so that even
 * what fails early - a route that finds no service, say - already has an id to show.
 */
@Component
public class EdgeCorrelationFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String received = exchange.getRequest().getHeaders().getFirst(HEADER);
        // An incoming id is honoured: if one day there were a proxy or a frontend in front
        // of the gateway that already traces calls, overwriting it would break exactly the
        // chain this filter exists to hold together.
        String id = (received == null || received.isBlank()) ? generate() : received;

        ServerWebExchange withId = exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, id)))
                .build();
        // In the exchange too, so that GatewayErrorHandler can quote it when it answers on
        // behalf of an unreachable service.
        withId.getAttributes().put(HEADER, id);
        // A set() before forwarding is not enough: the downstream service sends its own
        // X-Request-Id back, the gateway merges it with the one already there, and the
        // client ends up with the header TWICE (same value, but a list all the same).
        // beforeCommit runs after that merge, so here set() really does replace.
        withId.getResponse().beforeCommit(() -> {
            withId.getResponse().getHeaders().set(HEADER, id);
            return Mono.empty();
        });

        return chain.filter(withId);
    }

    /**
     * The id of a request, from wherever it is asked for.
     *
     * The order of the fallbacks is not arbitrary. The attribute is written by the filter
     * above, but that filter does NOT run when no route matches: in that case the 404 is
     * born in the mapping, before the chain. Re-reading the original header covers exactly
     * that gap, and keeps the caller's id even on a path that does not exist.
     */
    static String ofRequest(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(HEADER);
        if (attribute instanceof String saved && !saved.isBlank()) {
            return saved;
        }
        String received = exchange.getRequest().getHeaders().getFirst(HEADER);
        return (received == null || received.isBlank()) ? generate() : received;
    }

    /** The same shape as the downstream services: an id that changes form halfway confuses. */
    static String generate() {
        return "REQ_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
