package com.prenotazioni.booking.dto;

import lombok.Value;

/** Conteggio prenotazioni per stato, dentro AdminBookingsPayload. */
@Value
public class BookingStats {
    long totale;
    long attive;
    long annullate;
}
