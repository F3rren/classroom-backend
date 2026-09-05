package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Room;
import lombok.Getter;

/** Riepilogo di un'aula creata/modificata, riusato da createRoom e updateRoom. */
@Getter
public class RoomAckPayload {
    private final Long aulaId;
    private final String nome;
    private final int piano;
    private final int capienza;

    public RoomAckPayload(Room aula) {
        this.aulaId = aula.getId();
        this.nome = aula.getNome();
        this.piano = aula.getPiano();
        this.capienza = aula.getCapienza();
    }
}
