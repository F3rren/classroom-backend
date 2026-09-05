package com.prenotazioni.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;

/**
 * Lista di aule, riusata da RoomController e AdminController.
 * I campi opzionali (piano, capienzaMinima, type, suggestion, maxCapacityFound) sono
 * popolati solo dagli endpoint che li usavano gia' come chiavi extra nel Map.of originale;
 * @JsonInclude(NON_NULL) li omette per tutti gli altri, riproducendo lo shape esatto di oggi.
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

    public RoomListPayload withPiano(int floor) { this.floor = floor; return this; }
    public RoomListPayload withMinCapacity(int minCapacity) { this.minCapacity = minCapacity; return this; }
    public RoomListPayload withType(String type) { this.type = type; return this; }
    public RoomListPayload withSuggestion(String suggestion) { this.suggestion = suggestion; return this; }
    public RoomListPayload withMaxCapacityFound(int maxCapacityFound) { this.maxCapacityFound = maxCapacityFound; return this; }
}
