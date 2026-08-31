package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;

/** Risposta di successo per il blocco aula da parte di un admin (POST /blocca). */
public class BlockAckPayload {
    private final Prenotazione blocco;
    private final Long aulaId;
    private final String periodo;
    private final Long amministratore;

    public BlockAckPayload(Prenotazione blocco, Long aulaId, String periodo, Long amministratore) {
        this.blocco = blocco;
        this.aulaId = aulaId;
        this.periodo = periodo;
        this.amministratore = amministratore;
    }

    public Prenotazione getBlocco() { return blocco; }
    public Long getAulaId() { return aulaId; }
    public String getPeriodo() { return periodo; }
    public Long getAmministratore() { return amministratore; }
}
