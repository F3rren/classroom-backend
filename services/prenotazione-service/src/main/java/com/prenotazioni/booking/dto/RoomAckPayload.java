package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Room;
import lombok.Getter;

/** Riepilogo di un'aula creata/modificata, riusato da createRoom e updateRoom. */
@Getter
public class RoomAckPayload {
    private final Long roomId;
    private final String name;
    private final int floor;
    private final int capacity;

    public RoomAckPayload(Room room) {
        this.roomId = room.getId();
        this.name = room.getName();
        this.floor = room.getFloor();
        this.capacity = room.getCapacity();
    }
}
