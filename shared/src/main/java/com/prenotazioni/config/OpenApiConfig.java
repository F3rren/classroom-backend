package com.prenotazioni.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Definisce un unico schema di autenticazione Bearer JWT globale, cosi' Swagger UI
 * mostra un solo pulsante "Authorize" invece di un campo header per ogni endpoint.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Prenotazioni Aule API")
                        .description("API REST per la gestione di prenotazioni aule, corsi e notifiche")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
