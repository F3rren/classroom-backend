package com.prenotazioni.booking.dto;

import lombok.Value;

/** The per-status booking counts, inside AdminBookingsPayload. */
@Value
public class BookingStats {
    long totale;
    long attive;
    long annullate;
}
