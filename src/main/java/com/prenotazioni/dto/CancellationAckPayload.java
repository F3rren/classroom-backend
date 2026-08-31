package com.prenotazioni.dto;

import lombok.Value;

/** Risposta di successo per DELETE /api/prenotazioni/{id}. */
@Value
public class CancellationAckPayload {
    Long prenotazioneId;
    Long utenteId;
    String dataAnnullamento;
}
