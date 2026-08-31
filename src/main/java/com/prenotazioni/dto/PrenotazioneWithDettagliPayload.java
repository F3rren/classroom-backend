package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;

import java.util.List;

/** Risposta non avvolta di GET /api/prenotazioni/{id}/details. */
public class PrenotazioneWithDettagliPayload {
    private final Prenotazione prenotazione;
    private final List<PrenotazioneDettaglioDto> dettagliCompleti;
    private final int totalDettagli;

    public PrenotazioneWithDettagliPayload(Prenotazione prenotazione, List<PrenotazioneDettaglioDto> dettagliCompleti) {
        this.prenotazione = prenotazione;
        this.dettagliCompleti = dettagliCompleti;
        this.totalDettagli = dettagliCompleti.size();
    }

    public Prenotazione getPrenotazione() { return prenotazione; }
    public List<PrenotazioneDettaglioDto> getDettagliCompleti() { return dettagliCompleti; }
    public int getTotalDettagli() { return totalDettagli; }
}
