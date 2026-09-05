package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

/** Risposta di successo per il blocco aula da parte di un admin (POST /blocca). */
@Value
public class BlockAckPayload {
    Booking blocco;
    Long roomId;
    String periodo;
    Long amministratore;
}
