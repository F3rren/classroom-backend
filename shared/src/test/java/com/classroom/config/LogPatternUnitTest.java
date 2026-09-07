package com.classroom.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The log pattern, the one logback-spring.xml actually loads.
 *
 * It does not check the text of the file: it checks the encoder Logback built after reading
 * it, and puts a real event through it. That is the difference between "the file contains the
 * right string" and "a log line shows the id".
 *
 * It is needed because that field was invisible for a long time: RequestCorrelationFilter
 * had always been putting the id into the MDC, but without a pattern to print it, it appeared
 * nowhere - and no test would have noticed, because a missing log line does not
 * fallire niente.
 */
class LogPatternUnitTest {

    @Configuration
    static class SoloContesto {
    }

    @AfterEach
    void pulisci() {
        MDC.clear();
    }

    /** L'encoder dell'appender su console, come Logback l'ha costruito. */
    private PatternLayoutEncoder encoderConfigurato() {
        // The context has to start: it is Spring Boot that tells Logback to read
        // logback-spring.xml, and without it the default configuration would apply.
        try (var context = new SpringApplicationBuilder(SoloContesto.class)
                .web(WebApplicationType.NONE)
                .run()) {
            LoggerContext logback = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger radice = logback.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var appender = radice.getAppender("CONSOLE");
            assertThat(appender)
                    .as("l'appender CONSOLE non esiste: logback-spring.xml non e' stato caricato")
                    .isInstanceOf(ConsoleAppender.class);
            Encoder<?> encoder = ((ConsoleAppender<?>) appender).getEncoder();
            assertThat(encoder).isInstanceOf(PatternLayoutEncoder.class);
            return (PatternLayoutEncoder) encoder;
        }
    }

    /** Renders an event with the configured encoder, as a real line would be. */
    private String line(String message) {
        PatternLayoutEncoder encoder = encoderConfigurato();
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("com.classroom.prova.Servizio");
        event.setLevel(Level.INFO);
        event.setMessage(message);
        event.setMDCPropertyMap(MDC.getCopyOfContextMap() == null
                ? java.util.Map.of() : MDC.getCopyOfContextMap());
        event.setTimeStamp(System.currentTimeMillis());
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    @Test
    void lIdentificativoDiRichiestaCompareNellaRiga() {
        // THE reason this file exists. If it falls, the id goes back to sitting in the MDC
        // with nobody seeing it, and that is a failure that fails nothing: it is noticed only
        // on the day somebody needs to read the logs, which is too late.
        MDC.put("requestId", "REQ_A1B2C3D4");

        assertThat(line("prenotazione creata")).contains("REQ_A1B2C3D4");
    }

    @Test
    void unIdentificativoPiuLungoNonVieneTagliato() {
        // Found by looking at the real output, not at the file: with %-12.12X Logback
        // LEFT, and "REQ_READABLE" came out as "EQ_READABLE". The ids minted
        // here are exactly 12 characters long and never noticed, but the gateway reuses the
        // X-Request-Id it receives, which can be as long as the caller likes.
        //
        // An id silently cut short is worse than a misaligned column: two different values
        // can come out looking identical, and somebody searching the logs finds the request
        // sbagliata.
        MDC.put("requestId", "REQ_MOLTO_PIU_LUNGO_DEL_SOLITO");

        assertThat(line("qualcosa")).contains("REQ_MOLTO_PIU_LUNGO_DEL_SOLITO");
    }

    @Test
    void unaRigaFuoriDaUnaRichiestaNonDiceNull() {
        // Startup, scheduled jobs, message consumption: no request, no
        // identificativo. Stampare "null" sarebbe peggio di un segnaposto.
        String line = line("avvio completato");

        assertThat(line).doesNotContain("null").contains("avvio completato");
    }

    @Test
    void ilMessaggioEIlLivelloRestanoLeggibili() {
        // The pattern adds a column: it must not eat what was already there.
        MDC.put("requestId", "REQ_LEGGIBILE");
        String line = line("qualcosa e' successo");

        assertThat(line)
                .contains("INFO")
                .contains("qualcosa e' successo")
                .contains("Servizio");
    }
}
