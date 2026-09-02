package com.prenotazioni.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di creazione di un nuovo utente da parte di un admin (POST /api/admin/register).
 * La password e' obbligatoria qui, a differenza di UpdateUserRequest dove e' opzionale.
 */
@Data
@Schema(description = "Dati per creare un nuovo utente (solo amministratori)")
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Lo username è obbligatorio.")
    @Schema(description = "Username univoco, usato per il login e mostrato nelle liste", example = "m.rossi")
    private String username;

    @NotBlank(message = "L'email è obbligatoria.")
    @Email(message = "Il formato dell'email non è valido.")
    @Schema(description = "Email univoca dell'utente", example = "mario.rossi@example.it")
    private String email;

    @NotBlank(message = "La password è obbligatoria.")
    @Size(min = 8, message = "La password deve essere di almeno 8 caratteri.")
    @Schema(description = "Password in chiaro, salvata solo come hash BCrypt. Minimo 8 caratteri",
            example = "password-sicura", minLength = 8)
    private String password;

    @Pattern(regexp = "(?i)admin|user", message = "Il ruolo deve essere 'admin' o 'user'.")
    @Schema(description = "Ruolo applicativo. Se omesso l'utente viene creato come 'user'",
            allowableValues = {"admin", "user"}, example = "user")
    private String ruolo;

    @NotBlank(message = "Il nome è obbligatorio.")
    @Schema(description = "Nome e cognome mostrati nell'interfaccia", example = "Mario Rossi")
    private String nome;
}
