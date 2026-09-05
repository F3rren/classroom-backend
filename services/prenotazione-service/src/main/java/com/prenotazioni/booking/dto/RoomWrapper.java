package com.prenotazioni.booking.dto;

import lombok.Value;

/**
 * Wrapper minimo per una singola aula, riusato da GET /api/admin/rooms/{id} (Aula grezza)
 * e GET /api/rooms/{id}/detailed (RoomDetailsResponse).
 */
@Value
public class RoomWrapper<T> {
    T room;
}
