package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;

import java.util.List;

/** Vista admin di tutte le prenotazioni (incluse annullate), con statistiche per stato. */
public class AdminPrenotazioniPayload {
    private final List<Prenotazione> prenotazioni;
    private final PrenotazioniStats statistiche;

    public AdminPrenotazioniPayload(List<Prenotazione> prenotazioni, PrenotazioniStats statistiche) {
        this.prenotazioni = prenotazioni;
        this.statistiche = statistiche;
    }

    public List<Prenotazione> getPrenotazioni() { return prenotazioni; }
    public PrenotazioniStats getStatistiche() { return statistiche; }
}
