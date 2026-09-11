package com.classroom.booking.dto;

import com.classroom.booking.model.Room;

/** The summary of a created or updated room, reused by createRoom and updateRoom. */
public record RoomAckPayload(Long roomId, String name, int floor, int capacity) {

    public RoomAckPayload(Room room) {
        this(room.getId(), room.getName(), room.getFloor(), room.getCapacity());
    }
}
