package com.classroom.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A request from an admin to update an existing user (PUT /api/admin/users/{id}).
 * The password is optional: when empty or absent, AuthService.updateUser keeps the
 * existing one.
 */
@Schema(description = "The data to update an existing user (administrators only)")
public record UpdateUserRequest(
        @NotBlank(message = "Lo username è obbligatorio.")
        @Schema(description = "The new username, which has to stay unique", example = "m.rossi")
        String username,

        @NotBlank(message = "L'email è obbligatoria.")
        @Email(message = "Il formato dell'email non è valido.")
        @Schema(description = "The new email address, which has to stay unique", example = "mario.rossi@example.it")
        String email,

        @Schema(description = "The new password. Leave it empty to leave the password unchanged", example = "")
        String password,

        @Pattern(regexp = "(?i)admin|user", message = "Il ruolo deve essere 'admin' o 'user'.")
        @Schema(description = "The new role. When omitted the current one is kept",
                allowableValues = {"admin", "user"}, example = "user")
        String role,

        @NotBlank(message = "Il nome è obbligatorio.")
        @Schema(description = "The full name shown in the interface", example = "Mario Rossi")
        String name) {
}
