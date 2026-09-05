package com.prenotazioni.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Il messaggio che prenotazione-service manda quando un admin cancella una prenotazione.
 *
 * Porta con se' tutto cio' che la notifica deve mostrare (nome della stanza, orari, nome
 * dell'admin) invece del solo id della prenotazione: e' cio' che permette a questo
 * servizio di non richiamare indietro chi lo ha invocato per costruire il testo. La
 * notifica e' un fatto avvenuto, e ne conserva i dati come erano in quel momento.
 */
@Data
@Schema(description = "Dati di una prenotazione cancellata, per generare la notifica")
public class BookingCancellationRequest {

    @NotNull(message = "L'utente destinatario e' obbligatorio.")
    @Schema(description = "Destinatario della notifica", example = "7")
    private Long userId;

    @Schema(description = "Prenotazione cancellata", example = "42")
    private Long bookingId;

    @Size(max = 100)
    @Schema(description = "Nome dell'aula", example = "Aula Magna")
    private String roomName;

    @Size(max = 100)
    @Schema(description = "Chi ha cancellato; null se l'ha fatto l'utente stesso", example = "Mario Rossi")
    private String adminName;

    @Schema(description = "Giorno della prenotazione", example = "2026-12-25")
    private String bookingDate;

    @Schema(description = "Ora di inizio", example = "14:30")
    private String oraInizio;

    @Schema(description = "Ora di fine", example = "16:30")
    private String oraFine;

    @Size(max = 500)
    @Schema(description = "Motivo della cancellazione", example = "Aula richiesta per una sessione d'esame")
    private String motivo;
}
