package com.prenotazioni.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * L'identificativo della richiesta nasce qui, al bordo, e non dentro ogni servizio.
 *
 * I servizi a valle hanno gia' un filtro che riusa l'intestazione X-Request-Id se la
 * trovano (RequestCorrelationFilter, nel modulo shared) - ma finche' nessuno la manda, ogni
 * servizio se ne genera una propria e una chiamata che attraversa gateway e servizio
 * prenotazioni resta spezzata in due tronconi scollegati nei log. Coniarla qui e' cio' che
 * rende utile quel riuso: da questo punto in avanti tutti parlano della stessa richiesta.
 *
 * Il filtro sta il piu' vicino possibile all'ingresso (HIGHEST_PRECEDENCE) cosi' che anche
 * cio' che fallisce presto - un instradamento che non trova il servizio, per dire - abbia
 * gia' il suo identificativo da mostrare.
 */
@Component
public class EdgeCorrelationFilter implements GlobalFilter, Ordered {

    static final String INTESTAZIONE = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ricevuto = exchange.getRequest().getHeaders().getFirst(INTESTAZIONE);
        // Si rispetta quella in arrivo: se un giorno davanti al gateway ci fosse un
        // proxy o un frontend che gia' traccia le chiamate, sovrascriverla romperebbe
        // proprio la catena che questo filtro esiste per tenere insieme.
        String id = (ricevuto == null || ricevuto.isBlank()) ? generate() : ricevuto;

        ServerWebExchange withId = exchange.mutate()
                .request(r -> r.headers(h -> h.set(INTESTAZIONE, id)))
                .build();
        // Anche nell'exchange, cosi' GatewayErrorHandler puo' citarlo quando risponde
        // al posto di un servizio irraggiungibile.
        withId.getAttributes().put(INTESTAZIONE, id);
        // set() prima di inoltrare non basta: il servizio a valle rimanda a sua volta la
        // sua X-Request-Id, il gateway la unisce a quella gia' presente e il client si
        // ritrova l'intestazione DUE volte (stesso valore, ma comunque una lista).
        // beforeCommit gira dopo la fusione, quindi qui set() sostituisce davvero.
        withId.getResponse().beforeCommit(() -> {
            withId.getResponse().getHeaders().set(INTESTAZIONE, id);
            return Mono.empty();
        });

        return chain.filter(withId);
    }

    /**
     * L'identificativo di una richiesta, da qualunque punto lo si chieda.
     *
     * L'ordine dei ripieghi non e' casuale. L'attributo lo scrive il filtro qui sopra, ma
     * il filtro NON gira quando nessuna rotta corrisponde: in quel caso il 404 nasce nella
     * mappatura, prima della catena. Rileggere l'intestazione originale copre proprio
     * quel buco, e conserva l'id del chiamante anche su un percorso inesistente.
     */
    static String ofRequest(ServerWebExchange exchange) {
        Object attributo = exchange.getAttribute(INTESTAZIONE);
        if (attributo instanceof String saved && !saved.isBlank()) {
            return saved;
        }
        String ricevuto = exchange.getRequest().getHeaders().getFirst(INTESTAZIONE);
        return (ricevuto == null || ricevuto.isBlank()) ? generate() : ricevuto;
    }

    /** Lo stesso formato dei servizi a valle: un id che cambia forma a meta' strada confonde. */
    static String generate() {
        return "REQ_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
