package com.prenotazioni.setting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il file .env e' l'unico posto dove stanno i segreti, e lo leggono in due: Docker Compose
 * per iniettarli nei container, e Spring - tramite spring.config.import - per i lanci fuori
 * dai container. Questa classe verifica che la seconda meta' di quella frase sia vera.
 *
 * Il rischio che copre e' preciso e sgradevole: Compose e Java interpretano i valori in modo
 * leggermente diverso. Se divergessero, i due leggerebbero valori diversi DALLO STESSO FILE,
 * e il file sembrerebbe corretto guardandolo. E' un guasto peggiore di quello che il .env
 * unico e' venuto a chiudere, quindi vale la pena tenerlo fermo con dei test.
 *
 * Non si legge il .env vero: contiene segreti. Si verifica il MECCANISMO su file scritti qui.
 */
class FormatoEnvUnitTest {

    @TempDir
    Path cartella;

    @Configuration
    static class SoloAmbiente {
        // Nessun bean: serve solo un contesto che carichi le proprieta'. Un
        // @SpringBootApplication tirerebbe dentro la configurazione automatica del
        // datasource e il test smetterebbe di parlare di cio' che deve verificare.
    }

    private ConfigurableApplicationContext avviaCon(String contenutoEnv) throws IOException {
        Path env = cartella.resolve("prova.env");
        Files.writeString(env, contenutoEnv);
        return new SpringApplicationBuilder(SoloAmbiente.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=file:" + env.toAbsolutePath() + "[.properties]")
                .run();
    }

    @Test
    void springLeggeIlFormatoDiCompose() throws IOException {
        // La riga di chiusura: senza questa, l'intera scelta di tenere un solo file cade.
        try (var contesto = avviaCon("JWT_SECRET=abc123\nSPRING_DATASOURCE_PASSWORD=segreta\n")) {
            assertThat(contesto.getEnvironment().getProperty("JWT_SECRET")).isEqualTo("abc123");
            assertThat(contesto.getEnvironment().getProperty("SPRING_DATASOURCE_PASSWORD")).isEqualTo("segreta");
        }
    }

    @Test
    void ilSegnapostoRisolveDalEnv() throws IOException {
        // I nomi non combaciano - .env dice SPRING_DATASOURCE_PASSWORD, Spring vuole
        // spring.datasource.password - e la conversione automatica NON avviene: il relaxed
        // binding tratta le maiuscole con underscore solo per le variabili d'ambiente vere,
        // non per le chiavi lette da un file. Il ponte e' un segnaposto esplicito, ed e'
        // questo che il test fissa.
        Path env = cartella.resolve("prova.env");
        Files.writeString(env, "JWT_SECRET=chiave-di-prova\n");
        try (var contesto = new SpringApplicationBuilder(SoloAmbiente.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=file:" + env.toAbsolutePath() + "[.properties]",
                            "jwt.secret=${JWT_SECRET}")
                .run()) {
            assertThat(contesto.getEnvironment().getProperty("jwt.secret")).isEqualTo("chiave-di-prova");
        }
    }

    @Test
    void unSegretoBase64AttraversaIntatto() throws IOException {
        // I segreti veri sono base64: contengono + / = e possono finire con del padding.
        // Nessuno di questi ha significato speciale in un file properties, ma "nessuno di
        // questi" e' un'affermazione che va verificata, non ricordata.
        String segreto = "aB3+xY/9zQ==";
        try (var contesto = avviaCon("JWT_SECRET=" + segreto + "\n")) {
            assertThat(contesto.getEnvironment().getProperty("JWT_SECRET")).isEqualTo(segreto);
        }
    }

    @Test
    void leVirgoletteFinirebberoDentroAlValore() throws IOException {
        // Questa e' la divergenza vera fra i due lettori, ed e' il motivo per cui .env.example
        // dice di non usare virgolette: Compose le toglie, Java le tiene. Il test non la
        // corregge - la DOCUMENTA, perche' un giorno qualcuno virgoletta un segreto e il
        // sintomo sara' un token che non valida, senza niente che spieghi perche'.
        try (var contesto = avviaCon("JWT_SECRET=\"virgolettato\"\n")) {
            assertThat(contesto.getEnvironment().getProperty("JWT_SECRET"))
                    .isEqualTo("\"virgolettato\"")
                    .isNotEqualTo("virgolettato");
        }
    }

    @Test
    void unFileAssenteNonImpedisceLAvvio() {
        // In container il .env non c'e': i valori arrivano gia' come variabili d'ambiente,
        // iniettate da Compose. L'import e' "optional:" proprio per questo, e se cosi' non
        // fosse i quattro servizi non partirebbero affatto.
        try (var contesto = new SpringApplicationBuilder(SoloAmbiente.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=optional:file:"
                        + cartella.resolve("inesistente.env").toAbsolutePath() + "[.properties]")
                .run()) {
            assertThat(contesto.isActive()).isTrue();
        }
    }
}
