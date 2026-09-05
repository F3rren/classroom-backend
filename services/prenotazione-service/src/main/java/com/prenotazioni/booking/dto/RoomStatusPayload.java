package com.prenotazioni.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.time.LocalDateTime;

/** Risposta non avvolta di GET /api/bookings/status/{aulaId} (comportamento gia' esistente). */
@Value
@Schema(description = "Stato di occupazione di un'aula in questo momento")
public class RoomStatusPayload {
    @Schema(description = "Aula interrogata", example = "3")
    Long aulaId;
    @Schema(description = "Stato calcolato adesso. Vocabolario MAIUSCOLO, diverso da aula.stato persistito: "
            + "include PRENOTATA e non ha OCCUPATA",
            allowableValues = {"LIBERA", "PRENOTATA", "BLOCCATA", "MANUTENZIONE"}, example = "LIBERA")
    String stato;
    @Schema(description = "Momento a cui si riferisce lo stato")
    LocalDateTime timestamp;
}
