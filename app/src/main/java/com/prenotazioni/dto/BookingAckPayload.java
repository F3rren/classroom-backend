package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.Value;

/** Risposta di successo per creazione/modifica prenotazione (POST /prenota, PUT /{id}). */
@Value
public class BookingAckPayload {
    Prenotazione prenotazione;
    Long aulaId;
    String periodo;
}
