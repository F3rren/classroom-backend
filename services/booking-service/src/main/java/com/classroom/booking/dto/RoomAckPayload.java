package com.classroom.booking.dto;

import com.classroom.booking.model.Room;
import lombok.Getter;

/** The summary of a created or updated room, reused by createRoom and updateRoom. */
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
