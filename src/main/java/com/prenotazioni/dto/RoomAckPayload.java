package com.prenotazioni.dto;

import com.prenotazioni.model.Aula;

/** Riepilogo di un'aula creata/modificata, riusato da createRoom e updateRoom. */
public class RoomAckPayload {
    private final Long aulaId;
    private final String nome;
    private final int piano;
    private final int capienza;

    public RoomAckPayload(Aula aula) {
        this.aulaId = aula.getId();
        this.nome = aula.getNome();
        this.piano = aula.getPiano();
        this.capienza = aula.getCapienza();
    }

    public Long getAulaId() { return aulaId; }
    public String getNome() { return nome; }
    public int getPiano() { return piano; }
    public int getCapienza() { return capienza; }
}
