package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;

/** Risposta di successo per creazione/modifica prenotazione (POST /prenota, PUT /{id}). */
public class BookingAckPayload {
    private final Prenotazione prenotazione;
    private final Long aulaId;
    private final String periodo;

    public BookingAckPayload(Prenotazione prenotazione, Long aulaId, String periodo) {
        this.prenotazione = prenotazione;
        this.aulaId = aulaId;
        this.periodo = periodo;
    }

    public Prenotazione getPrenotazione() { return prenotazione; }
    public Long getAulaId() { return aulaId; }
    public String getPeriodo() { return periodo; }
}
