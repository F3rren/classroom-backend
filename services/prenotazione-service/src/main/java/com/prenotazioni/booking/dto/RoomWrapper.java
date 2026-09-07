package com.prenotazioni.booking.dto;

import lombok.Value;

/**
 * A minimal wrapper around a single room, reused by GET /api/admin/rooms/{id} (the raw Room)
 * e GET /api/rooms/{id}/detailed (RoomDetailsResponse).
 */
@Value
public class RoomWrapper<T> {
    T room;
}
