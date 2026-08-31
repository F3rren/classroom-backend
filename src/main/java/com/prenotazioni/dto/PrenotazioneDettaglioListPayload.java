package com.prenotazioni.dto;

import java.util.List;

/** Lista di dettagli prenotazione, non avvolta nell'envelope ApiEnvelope (comportamento gia' esistente). */
public class PrenotazioneDettaglioListPayload {
    private final List<PrenotazioneDettaglioDto> prenotazioni;
    private final int totalPrenotazioni;

    public PrenotazioneDettaglioListPayload(List<PrenotazioneDettaglioDto> prenotazioni) {
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }

    public List<PrenotazioneDettaglioDto> getPrenotazioni() { return prenotazioni; }
    public int getTotalPrenotazioni() { return totalPrenotazioni; }
}
