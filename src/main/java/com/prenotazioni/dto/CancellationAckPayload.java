package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Risposta di successo per DELETE /api/prenotazioni/{id}. */
@Getter
@AllArgsConstructor
public class CancellationAckPayload {
    private final Long prenotazioneId;
    private final Long utenteId;
    private final String dataAnnullamento;
}
