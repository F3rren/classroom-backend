package com.prenotazioni.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The gateway's error responses, in the same shape as the services' own.
 *
 * Without this class the gateway answered with Spring's default format:
 *
 *   {"timestamp":"2026-09-02T19:54:32.451+00:00","path":"/api/rooms",
 *    "status":500,"error":"Internal Server Error","requestId":"8e51fd93-51"}
 *
 * which has neither "success" nor "userMessage", and those are precisely the two fields a
 * frontend uses to decide whether and what to show. A client reading userMessage got
 * undefined every time the failure was the gateway's: that is, whenever a service is down,
 * which is exactly the moment a sensible message matters most.
 *
 * WHY THE ENVELOPE IS REBUILT BY HAND instead of reusing com.prenotazioni.dto.ApiEnvelope:
 * that class lives in shared, which brings spring-boot-starter-web with it. Adding it here
 * would start Tomcat instead of Netty and the gateway would stop being reactive. Between
 * duplicating seven field names and dragging in the servlet stack, the duplication is the
 * lesser evil - but it stays a risk of drift, so both sides have a test that pins the set
 * of keys (see ErrorResponsesGatewayTest and ApiEnvelopeUnitTest).
 *
 * @Order(-2) to come before DefaultErrorWebExceptionHandler, registered at -1.
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GatewayErrorHandler.class);

    /** The same format util.Timestamps uses in the services, not Spring's ISO. */
    private static final DateTimeFormatter API_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            // The response has already left: there is nothing useful left to do here except
            // avoid overwriting it halfway.
            return Mono.error(error);
        }

        Outcome outcome = classify(error);
        // The id is minted by EdgeCorrelationFilter on the way in, and the downstream
        // services reuse it: a 503 shown here therefore carries the same key the user would
        // have seen had the request reached its destination. The fallback covers the cases
        // where things fail before ever entering that filter.
        String sessionId = EdgeCorrelationFilter.ofRequest(exchange);
        // On a path matching no route the 404 is born before the GlobalFilters, so nobody
        // has written the header yet at this point.
        exchange.getResponse().getHeaders().set(EdgeCorrelationFilter.HEADER, sessionId);

        // The path goes in the log, not in the response: the client does not need it and
        // whoever is investigating does.
        logger.error("{} on {} -> {}: {}", outcome.code,
                exchange.getRequest().getPath(), outcome.status.value(), error.toString());

        exchange.getResponse().setStatusCode(outcome.status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer body = writeBody(exchange, outcome, sessionId);
        return exchange.getResponse().writeWith(Mono.just(body));
    }

    private DataBuffer writeBody(ServerWebExchange exchange, Outcome outcome, String sessionId) {
        // LinkedHashMap: the key order stays the one of the service envelope, which keeps
        // the two formats comparable by eye in logs and tools.
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", false);
        envelope.put("error", outcome.code);
        envelope.put("message", outcome.message);
        envelope.put("userMessage", outcome.userMessage);
        envelope.put("timestamp", LocalDateTime.now().format(API_TIMESTAMP_FORMAT));
        envelope.put("sessionId", sessionId);

        try {
            return exchange.getResponse().bufferFactory().wrap(objectMapper.writeValueAsBytes(envelope));
        } catch (IOException e) {
            // If even serialisation fails, a minimal hand-written body beats an empty
            // response: the client still has to find the shape it expects.
            logger.error("Envelope not serialisable: {}", e.getMessage());
            String minimal = "{\"success\":false,\"error\":\"INTERNAL_ERROR\"}";
            return exchange.getResponse().bufferFactory().wrap(minimal.getBytes());
        }
    }

    /**
     * Turns the exception into a status and a pair of messages.
     *
     * The distinction that matters is between "the service is not answering" and "the
     * gateway has a problem": the first is a 503 and temporary, so retrying is worth it;
     * the second is a 500 and retrying achieves nothing. Both used to be 500, and the
     * client had no way to tell them apart.
     *
     * userMessage stays Italian on purpose: it is the one string here that a person reads.
     */
    private Outcome classify(Throwable error) {
        if (error instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                return new Outcome(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Path not routed",
                        "La risorsa richiesta non esiste.");
            }
            return new Outcome(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, "GATEWAY_ERROR",
                    "Request refused by the gateway",
                    "La richiesta non e' stata accettata.");
        }

        if (unreachable(error)) {
            return new Outcome(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Downstream service unreachable",
                    "Il servizio non e' momentaneamente disponibile. Riprova fra qualche istante.");
        }

        return new Outcome(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Internal gateway error",
                "Si e' verificato un errore imprevisto. Riprova piu' tardi.");
    }

    /**
     * The downstream service was not reached.
     *
     * Several types are checked because the reason for not arriving changes the type of the
     * exception but not the answer to give. ConnectException is the obvious one (closed
     * port); UnknownHostException shows up when Netty's resolver fails to resolve the name,
     * which happens even with names as ordinary as "localhost" and has already led this
     * project astray once; Netty's connect timeout is a third case again.
     *
     * Compared by name rather than by class, so that Netty's internal types do not have to
     * become a dependency just to be named.
     */
    private boolean unreachable(Throwable error) {
        for (Throwable t = error; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return true;
            }
            if (t.getClass().getName().endsWith("ConnectTimeoutException")) {
                return true;
            }
        }
        return false;
    }

    private record Outcome(HttpStatus status, String code, String message, String userMessage) {
    }
}
