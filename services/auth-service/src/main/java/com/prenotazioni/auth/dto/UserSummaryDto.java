package com.prenotazioni.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.prenotazioni.model.Role;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.util.Timestamps;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * Rappresentazione pubblica di un Utente (mai la password), riusata da GET /api/me,
 * GET /api/admin/users e dall'oggetto "user" annidato nella risposta di login.
 * dataRegistrazione/ultimoAccesso sono omessi (non null) quando non impostati,
 * cosi' la risposta di login (che oggi non li include) resta identica.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Schema(description = "Vista pubblica di un utente. Non contiene mai la password")
public class UserSummaryDto {

    @Schema(description = "Identificativo dell'utente", example = "7")
    private Long id;
    @Schema(description = "Username univoco", example = "m.rossi")
    private String username;
    @Schema(description = "Nome e cognome", example = "Mario Rossi")
    private String name;
    @Schema(description = "Email dell'utente", example = "mario.rossi@example.it")
    private String email;
    @Schema(description = "Ruolo applicativo", example = "user")
    private Role role;
    @Schema(description = "Data di registrazione. Omessa quando non disponibile", example = "2026-01-15 09:30:00")
    private String registeredAt;
    @Schema(description = "Ultimo accesso. Omesso quando non disponibile", example = "2026-08-31 14:05:00")
    private String lastLogin;

    public static UserSummaryDto basic(User user) {
        UserSummaryDto dto = new UserSummaryDto();
        dto.id = user.getId();
        dto.username = user.getUsername() != null ? user.getUsername() : "";
        dto.name = user.getName() != null ? user.getName() : "";
        dto.email = user.getEmail() != null ? user.getEmail() : "";
        dto.role = user.getRole();
        return dto;
    }

    /** Usato da GET /api/me: ultimoAccesso non impostato "e' adesso", dataRegistrazione mancante e' omessa. */
    public static UserSummaryDto forProfile(User user) {
        UserSummaryDto dto = basic(user);
        // Timestamps.format restituisce null su input null: il ternario qui sarebbe ridondante
        dto.registeredAt = Timestamps.format(user.getRegisteredAt());
        dto.lastLogin = user.getLastLogin() != null
                ? Timestamps.format(user.getLastLogin())
                : Timestamps.now();
        return dto;
    }

    /** Usato da GET /api/admin/users: sia dataRegistrazione che ultimoAccesso mancanti diventano "" (mai omessi). */
    public static UserSummaryDto forAdminListing(User user) {
        UserSummaryDto dto = basic(user);
        dto.registeredAt = user.getRegisteredAt() != null
                ? Timestamps.format(user.getRegisteredAt()) : "";
        dto.lastLogin = user.getLastLogin() != null
                ? Timestamps.format(user.getLastLogin()) : "";
        return dto;
    }
}
