package com.classroom.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A list of rooms, reused by RoomController and AdminController.
 *
 * The optional fields (floor, minCapacity, type, suggestion, maxCapacityFound) are filled in
 * only by the endpoints that already used them as extra keys in the original Map.of;
 * @JsonInclude(NON_NULL) omits them for all the others, reproducing today's exact shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomListPayload(
        List<?> rooms,
        int totalRooms,
        Integer floor,
        Integer minCapacity,
        String type,
        String suggestion,
        Integer maxCapacityFound) {

    public static RoomListPayload of(List<?> rooms) {
        return new RoomListPayload(rooms, rooms.size(), null, null, null, null, null);
    }

    public RoomListPayload withFloor(int floor) {
        return new RoomListPayload(rooms, totalRooms, floor, minCapacity, type, suggestion, maxCapacityFound);
    }

    public RoomListPayload withMinCapacity(int minCapacity) {
        return new RoomListPayload(rooms, totalRooms, floor, minCapacity, type, suggestion, maxCapacityFound);
    }

    public RoomListPayload withType(String type) {
        return new RoomListPayload(rooms, totalRooms, floor, minCapacity, type, suggestion, maxCapacityFound);
    }

    public RoomListPayload withSuggestion(String suggestion) {
        return new RoomListPayload(rooms, totalRooms, floor, minCapacity, type, suggestion, maxCapacityFound);
    }

    public RoomListPayload withMaxCapacityFound(int maxCapacityFound) {
        return new RoomListPayload(rooms, totalRooms, floor, minCapacity, type, suggestion, maxCapacityFound);
    }
}
