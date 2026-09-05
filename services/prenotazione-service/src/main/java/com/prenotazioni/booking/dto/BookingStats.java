package com.prenotazioni.booking.dto;

import lombok.Value;

/** Conteggio prenotazioni per stato, dentro AdminPrenotazioniPayload. */
@Value
public class BookingStats {
    long totale;
    long attive;
    long annullate;
}
