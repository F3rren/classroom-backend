package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Risposta di successo per creazione/modifica prenotazione (POST /prenota, PUT /{id}). */
@Getter
@AllArgsConstructor
public class BookingAckPayload {
    private final Prenotazione prenotazione;
    private final Long aulaId;
    private final String periodo;
}
