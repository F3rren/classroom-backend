package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.Getter;

import java.util.List;

/** Risposta non avvolta di GET /api/prenotazioni/future: { prenotazioni, totalPrenotazioni }. */
@Getter
public class PrenotazioniListWithTotalPayload {
    private final List<Prenotazione> prenotazioni;
    private final int totalPrenotazioni;

    public PrenotazioniListWithTotalPayload(List<Prenotazione> prenotazioni) {
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }
}
