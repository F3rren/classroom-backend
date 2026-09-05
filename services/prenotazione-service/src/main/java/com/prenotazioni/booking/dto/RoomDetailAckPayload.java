package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Room;
import lombok.Getter;

/** Risposta di GET /api/rooms/{id}: l'aula piu' alcuni campi denormalizzati gia' presenti oggi. */
@Getter
public class RoomDetailAckPayload {
    private final Room room;
    private final Long roomId;
    private final String roomName;
    private final int floor;
    private final int capacity;

    public RoomDetailAckPayload(Room room) {
        this.room = room;
        this.roomId = room.getId();
        this.roomName = room.getName();
        this.floor = room.getFloor();
        this.capacity = room.getCapacity();
    }
}
