package com.prenotazioni.prenotazione.dto;

import lombok.Value;

/** Conteggio prenotazioni per stato, dentro AdminPrenotazioniPayload. */
@Value
public class PrenotazioniStats {
    long totale;
    long attive;
    long annullate;
}
