package com.prenotazioni.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Un solo identificativo per richiesta, condiviso da tutti quelli che la gestiscono.
 *
 * Prima ogni classe se ne generava uno per conto proprio: sette implementazioni con sei
 * prefissi diversi (ADM_, AUTH_, ME_, S, R, ERR_). La conseguenza pratica e' che una sola
 * richiesta finiva nei log con DUE identificativi distinti - quello del controller e
 * quello che GlobalExceptionHandler generava rispondendo - senza modo di collegarli. Il
 * campo che esiste per correlare i log non riusciva a farlo, che e' il peggior modo di
 * fallire per uno strumento di diagnosi: sembra funzionare.
 *
 * L'identificativo viaggia anche fra i servizi. Se la richiesta arriva con l'intestazione
 * X-Request-Id la si riusa invece di generarne una nuova: cosi' un giro che attraversa
 * gateway, servizio prenotazioni e servizio notifiche si segue con una sola chiave. In un
 * sistema a piu' servizi e' l'unico modo per ricostruire cosa sia successo.
 *
 * Finisce anche in MDC, quindi un pattern di log puo' stamparlo su ogni riga senza che
 * nessuno debba passarlo a mano.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelazioneRichiesta extends OncePerRequestFilter {

    public static final String INTESTAZIONE = "X-Request-Id";
    public static final String ATTRIBUTO = "com.prenotazioni.requestId";
    private static final String CHIAVE_MDC = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest richiesta, HttpServletResponse risposta,
                                    FilterChain catena) throws ServletException, IOException {
        String id = richiesta.getHeader(INTESTAZIONE);
        if (id == null || id.isBlank()) {
            id = genera();
        }

        richiesta.setAttribute(ATTRIBUTO, id);
        MDC.put(CHIAVE_MDC, id);
        // Rimandato indietro: chi ha fatto la chiamata puo' citarlo in una segnalazione
        // anche quando la risposta non ha un corpo in cui infilarlo.
        risposta.setHeader(INTESTAZIONE, id);

        try {
            catena.doFilter(richiesta, risposta);
        } finally {
            // Obbligatorio: i thread sono riusati, e un MDC non ripulito farebbe comparire
            // l'identificativo di una richiesta nei log di quella successiva.
            MDC.remove(CHIAVE_MDC);
        }
    }

    /**
     * L'identificativo della richiesta in corso.
     *
     * Il ripiego serve ai casi in cui non c'e' una richiesta HTTP: un consumatore di
     * messaggi, un'attivita' pianificata, un test unitario. Meglio un identificativo
     * scollegato che un null che poi compare come "null" dentro una risposta.
     */
    public static String corrente() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributi) {
            Object id = attributi.getRequest().getAttribute(ATTRIBUTO);
            if (id instanceof String stringa && !stringa.isBlank()) {
                return stringa;
            }
        }
        return genera();
    }

    private static String genera() {
        return "REQ_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
