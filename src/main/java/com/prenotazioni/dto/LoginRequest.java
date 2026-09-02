package com.prenotazioni.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Richiesta di login.
 *
 * ATTENZIONE: le @NotBlank qui sotto NON vengono applicate a runtime. AuthController.login
 * non annota il parametro con @Valid, quindi Bean Validation non gira affatto su questo DTO:
 * email e password vuote sono respinte dai controlli manuali dentro il controller.
 *
 * E' voluto e non va "sistemato" aggiungendo @Valid. Quei controlli devono girare DOPO il
 * rate limiter: Bean Validation li anticiperebbe, e un attaccante potrebbe far fallire la
 * validazione all'infinito senza mai consumare un tentativo dal rate limiter.
 *
 * Le annotazioni restano perche' springdoc le legge per generare lo schema OpenAPI:
 * verificato su /v3/api-docs, senza di esse LoginRequest.required perderebbe entrambi
 * i campi e la documentazione li mostrerebbe come facoltativi.
 */
@Data
@Schema(description = "Credenziali di accesso")
public class LoginRequest {

    @NotBlank(message = "L'email è obbligatoria per effettuare il login.")
    @Schema(description = "Email dell'account", example = "mario.rossi@example.it")
    private String email;

    @NotBlank(message = "La password è obbligatoria per effettuare il login.")
    @Schema(description = "Password dell'account", example = "password-sicura")
    private String password;
}
