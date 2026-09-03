package com.prenotazioni.prenotazione.dto;

import lombok.Getter;

import java.util.List;

/** Lista di dettagli prenotazione, non avvolta nell'envelope ApiEnvelope (comportamento gia' esistente). */
@Getter
public class PrenotazioneDettaglioListPayload {
    private final List<PrenotazioneDettaglioDto> prenotazioni;
    private final int totalPrenotazioni;

    public PrenotazioneDettaglioListPayload(List<PrenotazioneDettaglioDto> prenotazioni) {
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }
}
