package com.prenotazioni.dto;

/** Conteggio prenotazioni per stato, dentro AdminPrenotazioniPayload. */
public class PrenotazioniStats {
    private final long totale;
    private final long attive;
    private final long annullate;

    public PrenotazioniStats(long totale, long attive, long annullate) {
        this.totale = totale;
        this.attive = attive;
        this.annullate = annullate;
    }

    public long getTotale() { return totale; }
    public long getAttive() { return attive; }
    public long getAnnullate() { return annullate; }
}
