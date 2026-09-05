package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

/** Wrapper non avvolto in ApiEnvelope per GET /api/bookings/{id} (shape gia' esistente). */
@Value
public class BookingWrapper {
    Booking prenotazione;
}
