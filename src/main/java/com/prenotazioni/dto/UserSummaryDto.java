package com.prenotazioni.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.prenotazioni.model.Utente;
import com.prenotazioni.model.Ruolo;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Rappresentazione pubblica di un Utente (mai la password), riusata da GET /api/me,
 * GET /api/admin/users e dall'oggetto "user" annidato nella risposta di login.
 * dataRegistrazione/ultimoAccesso sono omessi (non null) quando non impostati,
 * cosi' la risposta di login (che oggi non li include) resta identica.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class UserSummaryDto {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String username;
    private String nome;
    private String email;
    private String ruolo;
    private String dataRegistrazione;
    private String ultimoAccesso;

    public static UserSummaryDto basic(Utente utente) {
        UserSummaryDto dto = new UserSummaryDto();
        dto.id = utente.getId();
        dto.username = utente.getUsername() != null ? utente.getUsername() : "";
        dto.nome = utente.getNome() != null ? utente.getNome() : "";
        dto.email = utente.getEmail() != null ? utente.getEmail() : "";
        dto.ruolo = utente.getRuolo() != null ? utente.getRuolo().getValore() : "USER";
        return dto;
    }

    /** Usato da GET /api/me: ultimoAccesso non impostato "e' adesso", dataRegistrazione mancante e' omessa. */
    public static UserSummaryDto forProfile(Utente utente) {
        UserSummaryDto dto = basic(utente);
        dto.dataRegistrazione = utente.getDataRegistrazione() != null
                ? utente.getDataRegistrazione().format(TIMESTAMP_FORMATTER) : null;
        dto.ultimoAccesso = utente.getUltimoAccesso() != null
                ? utente.getUltimoAccesso().format(TIMESTAMP_FORMATTER)
                : LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        return dto;
    }

    /** Usato da GET /api/admin/users: sia dataRegistrazione che ultimoAccesso mancanti diventano "" (mai omessi). */
    public static UserSummaryDto forAdminListing(Utente utente) {
        UserSummaryDto dto = basic(utente);
        dto.dataRegistrazione = utente.getDataRegistrazione() != null
                ? utente.getDataRegistrazione().format(TIMESTAMP_FORMATTER) : "";
        dto.ultimoAccesso = utente.getUltimoAccesso() != null
                ? utente.getUltimoAccesso().format(TIMESTAMP_FORMATTER) : "";
        return dto;
    }
}
