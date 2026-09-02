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
import java.util.UUID;

/**
 * Le risposte d'errore del gateway, nella stessa forma di quelle dei servizi.
 *
 * Senza questa classe il gateway rispondeva con il formato predefinito di Spring:
 *
 *   {"timestamp":"2026-09-02T19:54:32.451+00:00","path":"/api/rooms",
 *    "status":500,"error":"Internal Server Error","requestId":"8e51fd93-51"}
 *
 * che non ha ne' "success" ne' "userMessage", e cioe' proprio i due campi da cui il
 * frontend decide se e cosa mostrare. Un client che legge userMessage otteneva
 * undefined ogni volta che a fallire era il gateway: cioe' quando un servizio e' giu',
 * che e' esattamente il momento in cui un messaggio sensato serve di piu'.
 *
 * PERCHE' L'ENVELOPE E' RICOSTRUITO A MANO invece di riusare com.prenotazioni.dto.ApiEnvelope:
 * quella classe vive in shared, che porta spring-boot-starter-web. Aggiungerlo qui farebbe
 * partire Tomcat al posto di Netty e il gateway smetterebbe di essere reattivo. Fra
 * duplicare sette nomi di campo e trascinare dentro lo stack servlet, la duplicazione e'
 * il male minore - ma resta un rischio di divergenza, quindi entrambi i lati hanno un test
 * che ne blocca l'insieme delle chiavi (vedi RisposteErroreTest e ApiEnvelopeUnitTest).
 *
 * @Order(-2) per precedere DefaultErrorWebExceptionHandler, registrato a -1.
 */
@Component
@Order(-2)
public class GestoreErroriGateway implements ErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GestoreErroriGateway.class);

    /** Lo stesso formato usato da util.Timestamps nei servizi, non l'ISO di Spring. */
    private static final DateTimeFormatter FORMATO_API = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    GestoreErroriGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable errore) {
        if (exchange.getResponse().isCommitted()) {
            // La risposta e' gia' partita: qui non si puo' piu' fare nulla di utile
            // se non evitare di sovrascriverla a meta'.
            return Mono.error(errore);
        }

        Esito esito = classifica(errore);
        String sessionId = "GW_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Il percorso e' nel log, non nella risposta: al client non serve e a chi indaga si'.
        logger.error("[{}] {} su {} -> {}: {}", sessionId, esito.codice,
                exchange.getRequest().getPath(), esito.stato.value(), errore.toString());

        exchange.getResponse().setStatusCode(esito.stato);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer corpo = scrivi(exchange, esito, sessionId);
        return exchange.getResponse().writeWith(Mono.just(corpo));
    }

    private DataBuffer scrivi(ServerWebExchange exchange, Esito esito, String sessionId) {
        // LinkedHashMap: l'ordine delle chiavi resta quello dell'envelope dei servizi,
        // che rende i due formati confrontabili a occhio nei log e negli strumenti.
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", false);
        envelope.put("error", esito.codice);
        envelope.put("message", esito.messaggio);
        envelope.put("userMessage", esito.messaggioUtente);
        envelope.put("timestamp", LocalDateTime.now().format(FORMATO_API));
        envelope.put("sessionId", sessionId);

        try {
            return exchange.getResponse().bufferFactory().wrap(objectMapper.writeValueAsBytes(envelope));
        } catch (IOException e) {
            // Se persino la serializzazione fallisce, meglio un corpo minimo scritto a mano
            // che una risposta vuota: il client deve comunque trovare la forma che si aspetta.
            logger.error("[{}] Envelope non serializzabile: {}", sessionId, e.getMessage());
            String minimo = "{\"success\":false,\"error\":\"INTERNAL_ERROR\"}";
            return exchange.getResponse().bufferFactory().wrap(minimo.getBytes());
        }
    }

    /**
     * Traduce l'eccezione in stato e messaggi.
     *
     * La distinzione che conta e' fra "il servizio non risponde" e "il gateway ha un
     * problema": il primo e' 503 e temporaneo, quindi vale la pena riprovare; il secondo
     * e' 500 e riprovare non serve. Prima erano entrambi 500, e il client non poteva
     * distinguerli.
     */
    private Esito classifica(Throwable errore) {
        if (errore instanceof ResponseStatusException rse) {
            HttpStatus stato = HttpStatus.resolve(rse.getStatusCode().value());
            if (stato == HttpStatus.NOT_FOUND) {
                return new Esito(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Percorso non instradato",
                        "La risorsa richiesta non esiste.");
            }
            return new Esito(stato != null ? stato : HttpStatus.INTERNAL_SERVER_ERROR, "GATEWAY_ERROR",
                    "Richiesta rifiutata dal gateway",
                    "La richiesta non e' stata accettata.");
        }

        if (nonRaggiungibile(errore)) {
            return new Esito(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Servizio a valle non raggiungibile",
                    "Il servizio non e' momentaneamente disponibile. Riprova fra qualche istante.");
        }

        return new Esito(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Errore interno del gateway",
                "Si e' verificato un errore imprevisto. Riprova piu' tardi.");
    }

    /**
     * Il servizio a valle non e' stato raggiunto.
     *
     * Si controllano piu' tipi perche' il motivo per cui non si arriva a destinazione
     * cambia il tipo dell'eccezione ma non la risposta da dare. ConnectException e' il
     * caso ovvio (porta chiusa); UnknownHostException si presenta quando il resolver di
     * Netty non risolve il nome, cosa che capita anche con nomi banali come "localhost" e
     * che aveva gia' portato fuori strada una volta in questo progetto; il timeout di
     * connessione di Netty e' un terzo caso ancora.
     *
     * Confronto per nome e non per classe: cosi' non serve dipendere dai tipi interni di
     * Netty solo per nominarli.
     */
    private boolean nonRaggiungibile(Throwable errore) {
        for (Throwable t = errore; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return true;
            }
            if (t.getClass().getName().endsWith("ConnectTimeoutException")) {
                return true;
            }
        }
        return false;
    }

    private record Esito(HttpStatus stato, String codice, String messaggio, String messaggioUtente) {
    }
}
