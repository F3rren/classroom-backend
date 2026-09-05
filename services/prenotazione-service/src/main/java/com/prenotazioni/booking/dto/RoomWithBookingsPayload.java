package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Room;
import lombok.Getter;

import java.util.List;

/** Aula + le sue prenotazioni con dettagli, non avvolta nell'envelope ApiEnvelope (comportamento gia' esistente). */
@Getter
public class RoomWithBookingsPayload {
    private final Room aula;
    private final List<BookingDetailDto> prenotazioni;
    private final int totalPrenotazioni;

    public RoomWithBookingsPayload(Room aula, List<BookingDetailDto> prenotazioni) {
        this.aula = aula;
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }
}
