package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;

import java.util.List;

/** Risposta non avvolta di GET /api/prenotazioni/future: { prenotazioni, totalPrenotazioni }. */
public class PrenotazioniListWithTotalPayload {
    private final List<Prenotazione> prenotazioni;
    private final int totalPrenotazioni;

    public PrenotazioniListWithTotalPayload(List<Prenotazione> prenotazioni) {
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }

    public List<Prenotazione> getPrenotazioni() { return prenotazioni; }
    public int getTotalPrenotazioni() { return totalPrenotazioni; }
}
