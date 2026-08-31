package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;

import java.util.List;

/**
 * Lista prenotazioni con la sola chiave "prenotazioni" (nessun "totalPrenotazioni"),
 * riusata da GET /mie e dal ramo non-vuoto di GET (lista base) - shape gia' esistente.
 */
public class SinglePrenotazioniPayload {
    private final List<Prenotazione> prenotazioni;

    public SinglePrenotazioniPayload(List<Prenotazione> prenotazioni) {
        this.prenotazioni = prenotazioni;
    }

    public List<Prenotazione> getPrenotazioni() { return prenotazioni; }
}
