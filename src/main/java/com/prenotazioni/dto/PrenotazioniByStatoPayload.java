package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;

import java.util.List;

/** Risposta non avvolta di GET /api/prenotazioni/stato/{stato}. */
public class PrenotazioniByStatoPayload {
    private final String stato;
    private final List<Prenotazione> prenotazioni;
    private final int totalPrenotazioni;

    public PrenotazioniByStatoPayload(String stato, List<Prenotazione> prenotazioni) {
        this.stato = stato;
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }

    public String getStato() { return stato; }
    public List<Prenotazione> getPrenotazioni() { return prenotazioni; }
    public int getTotalPrenotazioni() { return totalPrenotazioni; }
}
