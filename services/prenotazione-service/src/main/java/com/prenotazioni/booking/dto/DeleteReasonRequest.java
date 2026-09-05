package com.prenotazioni.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Corpo opzionale per DELETE /api/admin/prenotazioni/{id}: motivo dell'eliminazione forzata. */
@Data
@Schema(description = "Motivo con cui un amministratore annulla la prenotazione di un altro utente. "
        + "Il corpo e' opzionale: se assente viene usato un motivo predefinito.")
public class DeleteReasonRequest {

    /**
     * Il limite non e' arbitrario: questo testo viene concatenato dentro Notifica.messaggio,
     * che e' varchar(1000), insieme al nome dell'aula, a quello dell'admin e alle date
     * (circa 330 caratteri di base). Senza limite il salvataggio della notifica falliva e
     * AdminController inghiottiva l'eccezione, annullando la prenotazione senza mai avvisare
     * il proprietario. Con il vincolo si ottiene invece un 400 esplicito.
     */
    @Size(max = 500, message = "Il motivo non puo' superare i 500 caratteri.")
    @Schema(description = "Motivo dell'annullamento, mostrato all'utente nella notifica",
            example = "Aula richiesta per una sessione d'esame", maxLength = 500)
    private String reason;
}
