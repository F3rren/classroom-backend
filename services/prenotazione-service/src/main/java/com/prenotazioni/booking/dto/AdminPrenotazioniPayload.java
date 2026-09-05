package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

import java.util.List;

/** Vista admin di tutte le prenotazioni (incluse annullate), con statistiche per stato. */
@Value
public class AdminPrenotazioniPayload {
    List<Booking> prenotazioni;
    BookingStats statistiche;
}
