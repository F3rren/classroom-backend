package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Room;
import lombok.Getter;

import java.util.List;

/** Aula + le sue prenotazioni con dettagli, non avvolta nell'envelope ApiEnvelope (comportamento gia' esistente). */
@Getter
public class RoomWithBookingsPayload {
    private final Room room;
    private final List<BookingDetailDto> bookings;
    private final int totalBookings;

    public RoomWithBookingsPayload(Room room, List<BookingDetailDto> bookings) {
        this.room = room;
        this.bookings = bookings;
        this.totalBookings = bookings.size();
    }
}
