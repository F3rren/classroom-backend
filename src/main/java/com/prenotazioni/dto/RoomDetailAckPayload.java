package com.prenotazioni.dto;

import com.prenotazioni.model.Aula;

/** Risposta di GET /api/rooms/{id}: l'aula piu' alcuni campi denormalizzati gia' presenti oggi. */
public class RoomDetailAckPayload {
    private final Aula room;
    private final Long roomId;
    private final String roomName;
    private final int floor;
    private final int capacity;

    public RoomDetailAckPayload(Aula room) {
        this.room = room;
        this.roomId = room.getId();
        this.roomName = room.getNome();
        this.floor = room.getPiano();
        this.capacity = room.getCapienza();
    }

    public Aula getRoom() { return room; }
    public Long getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public int getFloor() { return floor; }
    public int getCapacity() { return capacity; }
}
