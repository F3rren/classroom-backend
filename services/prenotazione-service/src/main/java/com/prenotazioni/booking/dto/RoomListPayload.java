package com.prenotazioni.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;

/**
 * A list of rooms, reused by RoomController and AdminController.
 *
 * The optional fields (floor, minCapacity, type, suggestion, maxCapacityFound) are filled in
 * only by the endpoints that already used them as extra keys in the original Map.of;
 * @JsonInclude(NON_NULL) omits them for all the others, reproducing today's exact shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class RoomListPayload {
    private List<?> rooms;
    private int totalRooms;
    private Integer floor;
    private Integer minCapacity;
    private String type;
    private String suggestion;
    private Integer maxCapacityFound;

    public static RoomListPayload of(List<?> rooms) {
        RoomListPayload p = new RoomListPayload();
        p.rooms = rooms;
        p.totalRooms = rooms.size();
        return p;
    }

    public RoomListPayload withFloor(int floor) { this.floor = floor; return this; }
    public RoomListPayload withMinCapacity(int minCapacity) { this.minCapacity = minCapacity; return this; }
    public RoomListPayload withType(String type) { this.type = type; return this; }
    public RoomListPayload withSuggestion(String suggestion) { this.suggestion = suggestion; return this; }
    public RoomListPayload withMaxCapacityFound(int maxCapacityFound) { this.maxCapacityFound = maxCapacityFound; return this; }
}
