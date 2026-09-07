package com.classroom.config;

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
 * The .env file is the only place the secrets live, and two things read it: Docker Compose
 * to inject them into the containers, and Spring - through spring.config.import - for runs
 * outside them. This class checks that the second half of that sentence is true.
 *
 * The risk it covers is precise and unpleasant: Compose and Java read the values slightly
 * leggermente diverso. Se divergessero, i due leggerebbero valori diversi DALLO STESSO FILE,
 * and the file would look correct to the eye. That is a worse failure than the one the
 * single .env came to close, so it is worth pinning down with tests.
 *
 * The real .env is never read: it holds secrets. What is checked is the MECHANISM, against
 * files written here.
 */
class EnvFormatUnitTest {

    @TempDir
    Path cartella;

    @Configuration
    static class SoloAmbiente {
        // No beans: all that is needed is a context that loads the properties. A
        // @SpringBootApplication would drag in the datasource auto-configuration and the
        // test would stop being about what it is meant to check.
    }

    private ConfigurableApplicationContext startWith(String contenutoEnv) throws IOException {
        Path env = cartella.resolve("prova.env");
        Files.writeString(env, contenutoEnv);
        return new SpringApplicationBuilder(SoloAmbiente.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=file:" + env.toAbsolutePath() + "[.properties]")
                .run();
    }

    @Test
    void springLeggeIlFormatoDiCompose() throws IOException {
        // The closing line: without this, the whole choice of keeping one single file falls.
        try (var context = startWith("JWT_SECRET=abc123\nSPRING_DATASOURCE_PASSWORD=segreta\n")) {
            assertThat(context.getEnvironment().getProperty("JWT_SECRET")).isEqualTo("abc123");
            assertThat(context.getEnvironment().getProperty("SPRING_DATASOURCE_PASSWORD")).isEqualTo("segreta");
        }
    }

    @Test
    void ilSegnapostoRisolveDalEnv() throws IOException {
        // The names do not line up - .env says SPRING_DATASOURCE_PASSWORD, Spring wants
        // spring.datasource.password - and the automatic conversion does NOT happen: relaxed
        // binding treats uppercase-with-underscores only for real environment variables, not
        // for keys read from a file. The bridge is an explicit placeholder, and that is what
        // this test pins down.
        Path env = cartella.resolve("prova.env");
        Files.writeString(env, "JWT_SECRET=chiave-di-prova\n");
        try (var context = new SpringApplicationBuilder(SoloAmbiente.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=file:" + env.toAbsolutePath() + "[.properties]",
                            "jwt.secret=${JWT_SECRET}")
                .run()) {
            assertThat(context.getEnvironment().getProperty("jwt.secret")).isEqualTo("chiave-di-prova");
        }
    }

    @Test
    void unSegretoBase64AttraversaIntatto() throws IOException {
        // Real secrets are base64: they contain + / = and can end in padding.
        // Nessuno di questi ha significato speciale in un file properties, ma "nessuno di
        // these" is a claim that has to be checked, not remembered.
        String secret = "aB3+xY/9zQ==";
        try (var context = startWith("JWT_SECRET=" + secret + "\n")) {
            assertThat(context.getEnvironment().getProperty("JWT_SECRET")).isEqualTo(secret);
        }
    }

    @Test
    void leVirgoletteFinirebberoDentroAlValore() throws IOException {
        // This is the real divergence between the two readers, and the reason .env.example
        // says not to use quotes: Compose strips them, Java keeps them. The test does not fix
        // it - it DOCUMENTS it, because one day somebody will quote a secret and the symptom
        // will be a token that does not validate, with nothing to explain why.
        try (var context = startWith("JWT_SECRET=\"virgolettato\"\n")) {
            assertThat(context.getEnvironment().getProperty("JWT_SECRET"))
                    .isEqualTo("\"virgolettato\"")
                    .isNotEqualTo("virgolettato");
        }
    }

    @Test
    void unFileAssenteNonImpedisceLAvvio() {
        // In a container there is no .env: the values arrive as environment variables
        // already, injected by Compose. The import is "optional:" precisely for that, and
        // were it not, the four services would not start at all.
        try (var context = new SpringApplicationBuilder(SoloAmbiente.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=optional:file:"
                        + cartella.resolve("inesistente.env").toAbsolutePath() + "[.properties]")
                .run()) {
            assertThat(context.isActive()).isTrue();
        }
    }
}
