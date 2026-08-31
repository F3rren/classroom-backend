package com.prenotazioni.dto;

/**
 * Wrapper minimo per una singola aula, riusato da GET /api/admin/rooms/{id} (Aula grezza)
 * e GET /api/rooms/{id}/detailed (RoomDetailsResponse).
 */
public class RoomWrapper<T> {
    private final T room;

    public RoomWrapper(T room) {
        this.room = room;
    }

    public T getRoom() { return room; }
}
