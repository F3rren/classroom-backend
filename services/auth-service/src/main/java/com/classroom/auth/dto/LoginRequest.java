package com.classroom.auth.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A login request.
 *
 * CAREFUL: the @NotBlank annotations below are NOT applied at runtime. AuthController.login
 * does not annotate the parameter with @Valid, so Bean Validation never runs on this DTO:
 * empty emails and passwords are rejected by the manual checks inside the controller.
 *
 * That is deliberate and must not be "fixed" by adding @Valid. Those checks have to run
 * AFTER the rate limiter: Bean Validation would come first, and an attacker could fail
 * validation forever without ever consuming an attempt from the limiter.
 *
 * The annotations stay because springdoc reads them to generate the OpenAPI schema: checked
 * on /v3/api-docs, without them LoginRequest.required would lose both fields and the
 * documentation would show them as optional.
 */
@Schema(description = "The login credentials")
public record LoginRequest(
        @NotBlank(message = "L'email è obbligatoria per effettuare il login.")
        @Schema(description = "The account email address", example = "mario.rossi@example.it")
        String email,

        @NotBlank(message = "La password è obbligatoria per effettuare il login.")
        @Schema(description = "The account password", example = "password-sicura")
        String password) {
}
