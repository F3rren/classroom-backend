package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;

/** Wrapper non avvolto in ApiEnvelope per GET /api/prenotazioni/{id} (shape gia' esistente). */
public class PrenotazioneWrapper {
    private final Prenotazione prenotazione;

    public PrenotazioneWrapper(Prenotazione prenotazione) {
        this.prenotazione = prenotazione;
    }

    public Prenotazione getPrenotazione() { return prenotazione; }
}
