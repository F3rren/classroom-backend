package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Conteggio prenotazioni per stato, dentro AdminPrenotazioniPayload. */
@Getter
@AllArgsConstructor
public class PrenotazioniStats {
    private final long totale;
    private final long attive;
    private final long annullate;
}
