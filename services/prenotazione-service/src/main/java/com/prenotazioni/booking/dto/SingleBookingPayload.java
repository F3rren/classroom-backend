package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

import java.util.List;

/**
 * Lista prenotazioni con la sola chiave "prenotazioni" (nessun "totalPrenotazioni"),
 * riusata da GET /mie e dal ramo non-vuoto di GET (lista base) - shape gia' esistente.
 */
@Value
public class SingleBookingPayload {
    List<Booking> prenotazioni;
}
