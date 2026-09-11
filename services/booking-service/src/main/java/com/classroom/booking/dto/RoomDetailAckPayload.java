package com.classroom.booking.dto;

import com.classroom.booking.model.Room;

/** The response of GET /api/rooms/{id}: the room plus a few denormalised fields already there. */
public record RoomDetailAckPayload(Room room, Long roomId, String roomName, int floor, int capacity) {

    public RoomDetailAckPayload(Room room) {
        this(room, room.getId(), room.getName(), room.getFloor(), room.getCapacity());
    }
}
