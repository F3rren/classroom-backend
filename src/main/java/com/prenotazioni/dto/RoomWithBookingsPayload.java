package com.prenotazioni.dto;

import com.prenotazioni.model.Aula;

import java.util.List;

/** Aula + le sue prenotazioni con dettagli, non avvolta nell'envelope ApiEnvelope (comportamento gia' esistente). */
public class RoomWithBookingsPayload {
    private final Aula aula;
    private final List<PrenotazioneDettaglioDto> prenotazioni;
    private final int totalPrenotazioni;

    public RoomWithBookingsPayload(Aula aula, List<PrenotazioneDettaglioDto> prenotazioni) {
        this.aula = aula;
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }

    public Aula getAula() { return aula; }
    public List<PrenotazioneDettaglioDto> getPrenotazioni() { return prenotazioni; }
    public int getTotalPrenotazioni() { return totalPrenotazioni; }
}
