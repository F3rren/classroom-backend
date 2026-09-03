package com.prenotazioni.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La documentazione OpenAPI e' servita in dev e disattivata in prod. Questo test non
 * verifica la resa grafica ma che il bean si costruisca e dichiari lo schema di sicurezza:
 * senza "bearerAuth" la Swagger UI non offre il campo per il token e diventa inutilizzabile
 * su ogni endpoint autenticato, cioe' tutti tranne il login.
 */
class OpenApiConfigUnitTest {

    @Test
    void theApiDeclaresItsIdentityAndTheBearerScheme() {
        OpenAPI openAPI = new OpenApiConfig().prenotazioniOpenAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Prenotazioni Aule API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openAPI.getSecurity()).hasSize(1);
        assertThat(openAPI.getSecurity().get(0)).containsKey("bearerAuth");
    }
}
