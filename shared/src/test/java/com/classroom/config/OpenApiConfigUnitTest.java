package com.classroom.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenAPI documentation is served in dev and switched off in prod. This test does not
 * check how it looks but that the bean builds and declares the security scheme: without
 * "bearerAuth" the Swagger UI offers no field for the token and becomes unusable on every
 * authenticated endpoint, which is all of them except the login.
 *
 * Built with "new" and not through Spring, so the @Value fields never get injected - what is
 * asserted here is exactly their plain-Java defaults, which are booking-service's own values.
 * Each service's real title, description and server URL are covered by nothing but running
 * the app: there is no per-service Spring context in this module to assert them against.
 */
class OpenApiConfigUnitTest {

    @Test
    void theApiDeclaresItsIdentityAndTheBearerScheme() {
        OpenAPI openAPI = new OpenApiConfig().openAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Prenotazioni Aule API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openAPI.getSecurity()).hasSize(1);
        assertThat(openAPI.getSecurity().get(0)).containsKey("bearerAuth");
        assertThat(openAPI.getServers()).hasSize(1);
        assertThat(openAPI.getServers().get(0).getUrl()).isEqualTo("http://localhost:17102");
    }
}
