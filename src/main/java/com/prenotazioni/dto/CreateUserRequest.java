package com.prenotazioni.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di creazione di un nuovo utente da parte di un admin (POST /api/admin/register).
 * La password e' obbligatoria qui, a differenza di UpdateUserRequest dove e' opzionale.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Lo username è obbligatorio.")
    private String username;

    @NotBlank(message = "L'email è obbligatoria.")
    @Email(message = "Il formato dell'email non è valido.")
    private String email;

    @NotBlank(message = "La password è obbligatoria.")
    @Size(min = 8, message = "La password deve essere di almeno 8 caratteri.")
    private String password;

    @Pattern(regexp = "(?i)admin|user", message = "Il ruolo deve essere 'admin' o 'user'.")
    private String ruolo;

    @NotBlank(message = "Il nome è obbligatorio.")
    private String nome;
}
