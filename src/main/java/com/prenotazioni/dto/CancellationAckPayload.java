package com.prenotazioni.dto;

/** Risposta di successo per DELETE /api/prenotazioni/{id}. */
public class CancellationAckPayload {
    private final Long prenotazioneId;
    private final Long utenteId;
    private final String dataAnnullamento;

    public CancellationAckPayload(Long prenotazioneId, Long utenteId, String dataAnnullamento) {
        this.prenotazioneId = prenotazioneId;
        this.utenteId = utenteId;
        this.dataAnnullamento = dataAnnullamento;
    }

    public Long getPrenotazioneId() { return prenotazioneId; }
    public Long getUtenteId() { return utenteId; }
    public String getDataAnnullamento() { return dataAnnullamento; }
}
