package com.prenotazioni.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di modifica di un utente esistente da parte di un admin (PUT /api/admin/users/{id}).
 * La password e' opzionale: se vuota/assente, AuthService.updateUtente mantiene quella esistente.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = "Lo username è obbligatorio.")
    private String username;

    @NotBlank(message = "L'email è obbligatoria.")
    @Email(message = "Il formato dell'email non è valido.")
    private String email;

    private String password;

    @Pattern(regexp = "(?i)admin|user", message = "Il ruolo deve essere 'admin' o 'user'.")
    private String ruolo;

    @NotBlank(message = "Il nome è obbligatorio.")
    private String nome;
}
