package com.classroom.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A request from an admin to create a new user (POST /api/admin/users).
 * The password is mandatory here, unlike in UpdateUserRequest where it is optional.
 */
@Data
@Schema(description = "The data to create a new user (administrators only)")
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Lo username è obbligatorio.")
    @Schema(description = "The unique username, used to log in and shown in listings", example = "m.rossi")
    private String username;

    @NotBlank(message = "L'email è obbligatoria.")
    @Email(message = "Il formato dell'email non è valido.")
    @Schema(description = "The user's unique email address", example = "mario.rossi@example.it")
    private String email;

    @NotBlank(message = "La password è obbligatoria.")
    @Size(min = 8, message = "La password deve essere di almeno 8 caratteri.")
    @Schema(description = "The password in clear, stored only as a BCrypt hash. At least 8 characters",
            example = "password-sicura", minLength = 8)
    private String password;

    @Pattern(regexp = "(?i)admin|user", message = "Il ruolo deve essere 'admin' o 'user'.")
    @Schema(description = "The application role. When omitted the user is created as 'user'",
            allowableValues = {"admin", "user"}, example = "user")
    private String role;

    @NotBlank(message = "Il nome è obbligatorio.")
    @Schema(description = "The full name shown in the interface", example = "Mario Rossi")
    private String name;
}
