package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Wrapper minimo per una singola aula, riusato da GET /api/admin/rooms/{id} (Aula grezza)
 * e GET /api/rooms/{id}/detailed (RoomDetailsResponse).
 */
@Getter
@AllArgsConstructor
public class RoomWrapper<T> {
    private final T room;
}
