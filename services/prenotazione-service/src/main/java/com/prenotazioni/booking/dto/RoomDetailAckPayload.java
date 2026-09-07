package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Room;
import lombok.Getter;

/** The response of GET /api/rooms/{id}: the room plus a few denormalised fields already there. */
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
