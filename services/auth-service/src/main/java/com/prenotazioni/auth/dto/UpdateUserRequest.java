package com.prenotazioni.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di modifica di un utente esistente da parte di un admin (PUT /api/admin/utenti/{id}).
 * La password e' opzionale: se vuota/assente, AuthService.updateUtente mantiene quella esistente.
 */
@Data
@Schema(description = "Dati per modificare un utente esistente (solo amministratori)")
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = "Lo username è obbligatorio.")
    @Schema(description = "Nuovo username, deve restare univoco", example = "m.rossi")
    private String username;

    @NotBlank(message = "L'email è obbligatoria.")
    @Email(message = "Il formato dell'email non è valido.")
    @Schema(description = "Nuova email, deve restare univoca", example = "mario.rossi@example.it")
    private String email;

    @Schema(description = "Nuova password. Lasciare vuoto per NON modificarla", example = "")
    private String password;

    @Pattern(regexp = "(?i)admin|user", message = "Il ruolo deve essere 'admin' o 'user'.")
    @Schema(description = "Nuovo ruolo. Se omesso resta quello attuale",
            allowableValues = {"admin", "user"}, example = "user")
    private String ruolo;

    @NotBlank(message = "Il nome è obbligatorio.")
    @Schema(description = "Nome e cognome mostrati nell'interfaccia", example = "Mario Rossi")
    private String nome;
}
