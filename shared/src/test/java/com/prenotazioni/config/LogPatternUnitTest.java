package com.prenotazioni.config;

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
 * Il tracciato dei log, quello davvero caricato da logback-spring.xml.
 *
 * Non verifica il testo del file: verifica l'encoder che Logback ha costruito dopo averlo
 * letto, e ci fa passare attraverso un evento vero. E' la differenza fra "il file contiene
 * la stringa giusta" e "una riga di log mostra l'identificativo".
 *
 * Serve perche' quel campo e' stato invisibile a lungo: RequestCorrelationFilter metteva
 * l'identificativo in MDC da sempre, ma senza un tracciato che lo stampasse non compariva
 * da nessuna parte - e nessun test se ne sarebbe accorto, perche' un log mancante non fa
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
        // Il contesto va avviato: e' Spring Boot a dire a Logback di leggere
        // logback-spring.xml, e senza di lui varrebbe la configurazione predefinita.
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

    /** Rende un evento con l'encoder configurato, come farebbe una riga vera. */
    private String line(String message) {
        PatternLayoutEncoder encoder = encoderConfigurato();
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("com.prenotazioni.prova.Servizio");
        event.setLevel(Level.INFO);
        event.setMessage(message);
        event.setMDCPropertyMap(MDC.getCopyOfContextMap() == null
                ? java.util.Map.of() : MDC.getCopyOfContextMap());
        event.setTimeStamp(System.currentTimeMillis());
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    @Test
    void lIdentificativoDiRichiestaCompareNellaRiga() {
        // LA ragione per cui questo file esiste. Se cade, l'identificativo torna a stare in
        // MDC senza che nessuno lo veda, ed e' un guasto che non fa fallire nulla: si nota
        // solo il giorno in cui serve leggere i log, cioe' troppo tardi.
        MDC.put("requestId", "REQ_A1B2C3D4");

        assertThat(line("prenotazione creata")).contains("REQ_A1B2C3D4");
    }

    @Test
    void unIdentificativoPiuLungoNonVieneTagliato() {
        // Trovato guardando l'output vero, non il file: con %-12.12X Logback tronca DA
        // SINISTRA, e "REQ_LEGGIBILE" usciva come "EQ_LEGGIBILE". Gli identificativi coniati
        // qui sono di 12 caratteri esatti e non se ne accorgevano, ma il gateway riusa
        // l'X-Request-Id che riceve, che puo' essere lungo quanto vuole chi chiama.
        //
        // Un identificativo tagliato in silenzio e' peggio di una colonna disallineata: due
        // valori diversi possono comparire identici, e chi cerca nei log trova la richiesta
        // sbagliata.
        MDC.put("requestId", "REQ_MOLTO_PIU_LUNGO_DEL_SOLITO");

        assertThat(line("qualcosa")).contains("REQ_MOLTO_PIU_LUNGO_DEL_SOLITO");
    }

    @Test
    void unaRigaFuoriDaUnaRichiestaNonDiceNull() {
        // Avvio, attivita' pianificate, consumo di messaggi: nessuna richiesta, nessun
        // identificativo. Stampare "null" sarebbe peggio di un segnaposto.
        String line = line("avvio completato");

        assertThat(line).doesNotContain("null").contains("avvio completato");
    }

    @Test
    void ilMessaggioEIlLivelloRestanoLeggibili() {
        // Il tracciato aggiunge una colonna: non deve mangiarsi cio' che c'era prima.
        MDC.put("requestId", "REQ_LEGGIBILE");
        String line = line("qualcosa e' successo");

        assertThat(line)
                .contains("INFO")
                .contains("qualcosa e' successo")
                .contains("Servizio");
    }
}
