package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

/** The success response when an admin blocks a room (POST /block). */
@Value
public class BlockAckPayload {
    Booking blocco;
    Long roomId;
    String period;
    Long amministratore;
}
