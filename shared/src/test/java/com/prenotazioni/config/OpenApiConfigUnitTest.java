package com.prenotazioni.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenAPI documentation is served in dev and switched off in prod. This test does not
 * check how it looks but that the bean builds and declares the security scheme: without
 * "bearerAuth" the Swagger UI offers no field for the token and becomes unusable on every
 * authenticated endpoint, which is all of them except the login.
 */
class OpenApiConfigUnitTest {

    @Test
    void theApiDeclaresItsIdentityAndTheBearerScheme() {
        OpenAPI openAPI = new OpenApiConfig().bookingsOpenAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Prenotazioni Aule API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openAPI.getSecurity()).hasSize(1);
        assertThat(openAPI.getSecurity().get(0)).containsKey("bearerAuth");
    }
}
