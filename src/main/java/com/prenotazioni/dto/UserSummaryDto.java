package com.prenotazioni.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.model.Utente;
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
    private String nome;
    @Schema(description = "Email dell'utente", example = "mario.rossi@example.it")
    private String email;
    @Schema(description = "Ruolo applicativo", example = "user")
    private Ruolo ruolo;
    @Schema(description = "Data di registrazione. Omessa quando non disponibile", example = "2026-01-15 09:30:00")
    private String dataRegistrazione;
    @Schema(description = "Ultimo accesso. Omesso quando non disponibile", example = "2026-08-31 14:05:00")
    private String ultimoAccesso;

    public static UserSummaryDto basic(Utente utente) {
        UserSummaryDto dto = new UserSummaryDto();
        dto.id = utente.getId();
        dto.username = utente.getUsername() != null ? utente.getUsername() : "";
        dto.nome = utente.getNome() != null ? utente.getNome() : "";
        dto.email = utente.getEmail() != null ? utente.getEmail() : "";
        dto.ruolo = utente.getRuolo();
        return dto;
    }

    /** Usato da GET /api/me: ultimoAccesso non impostato "e' adesso", dataRegistrazione mancante e' omessa. */
    public static UserSummaryDto forProfile(Utente utente) {
        UserSummaryDto dto = basic(utente);
        // Timestamps.format restituisce null su input null: il ternario qui sarebbe ridondante
        dto.dataRegistrazione = Timestamps.format(utente.getDataRegistrazione());
        dto.ultimoAccesso = utente.getUltimoAccesso() != null
                ? Timestamps.format(utente.getUltimoAccesso())
                : Timestamps.now();
        return dto;
    }

    /** Usato da GET /api/admin/users: sia dataRegistrazione che ultimoAccesso mancanti diventano "" (mai omessi). */
    public static UserSummaryDto forAdminListing(Utente utente) {
        UserSummaryDto dto = basic(utente);
        dto.dataRegistrazione = utente.getDataRegistrazione() != null
                ? Timestamps.format(utente.getDataRegistrazione()) : "";
        dto.ultimoAccesso = utente.getUltimoAccesso() != null
                ? Timestamps.format(utente.getUltimoAccesso()) : "";
        return dto;
    }
}
