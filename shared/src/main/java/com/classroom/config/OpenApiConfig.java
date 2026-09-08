package com.classroom.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Declares a single global Bearer JWT authentication scheme, so Swagger UI shows one
 * "Authorize" button instead of a header field on every endpoint. Shared because all three
 * services need the exact same scheme - it is one JWT, verified the same way everywhere.
 *
 * Title and description are @Value-injected, one property pair per service
 * (classroom.openapi.title / .description in each application.properties), because this
 * class is picked up by all three via their @ComponentScan of com.classroom.config: without
 * that, every service would show booking-service's own title, which used to be exactly what
 * happened - the field defaults below are its values, so anything constructed outside Spring
 * (OpenApiConfigUnitTest included, which builds this with "new" and so never receives the
 * injected value) keeps behaving as it always did.
 *
 * The server URL is @Value-injected too, and for a different reason: left unset, springdoc
 * fills it in from whatever path the request arrived on, and the aggregated Swagger UI (see
 * gateway/application.yml) fetches each spec through a /docs/&lt;service&gt;/v3/api-docs
 * proxy route - so the auto-detected server became "http://localhost:17102/docs/auth-service"
 * instead of the real API base, and every "Try it out" 404'd on a path the gateway never
 * routes. Declaring the server explicitly is what stops springdoc from guessing: the real
 * base is always the gateway, on 17102, for every service, whether its docs are opened
 * directly on its own port or through the aggregator - the endpoints themselves only exist
 * under /api/**, reachable through the gateway either way.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    @Value("${classroom.openapi.title:Prenotazioni Aule API}")
    private String title = "Prenotazioni Aule API";

    @Value("${classroom.openapi.description:API REST per la gestione di prenotazioni aule, corsi e notifiche}")
    private String description = "API REST per la gestione di prenotazioni aule, corsi e notifiche";

    @Value("${classroom.public-api-url:http://localhost:17102}")
    private String publicApiUrl = "http://localhost:17102";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .servers(List.of(new Server().url(publicApiUrl)));
    }
}
