package com.prenotazioni.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** The optional body of DELETE /api/admin/bookings/{id}: the reason for the forced delete. */
@Data
@Schema(description = "The reason an administrator gives for cancelling another user's booking. "
        + "Il corpo e' opzionale: se assente viene usato un motivo predefinito.")
public class DeleteReasonRequest {

    /**
     * The limit is not arbitrary: this text is concatenated into Notification.message, which
     * is varchar(1000), together with the room name, the admin name and the dates (roughly
     * 330 characters of scaffolding). Without a limit the notification save failed and
     * AdminController swallowed the exception, cancelling the booking without ever telling
     * the owner. With the constraint you get an explicit 400 instead.
     *
     * The @Size message stays Italian: it is a validation message, and it reaches the user.
     */
    @Size(max = 500, message = "Il motivo non puo' superare i 500 caratteri.")
    @Schema(description = "The reason for the cancellation, shown to the user in the notification",
            example = "Aula richiesta per una sessione d'esame", maxLength = 500)
    private String reason;
}
