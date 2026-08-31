package com.prenotazioni.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Richiesta di login. Solo @NotBlank qui: il formato email e la lunghezza minima
 * password restano controlli manuali nel controller, ESEGUITI DOPO il rate limiter -
 * spostarli in Bean Validation li farebbe girare prima, indebolendo la protezione
 * anti brute-force (un attaccante potrebbe far fallire la validazione senza mai
 * consumare un tentativo dal rate limiter).
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
