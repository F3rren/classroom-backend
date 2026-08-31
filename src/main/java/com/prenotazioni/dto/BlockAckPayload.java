package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Risposta di successo per il blocco aula da parte di un admin (POST /blocca). */
@Getter
@AllArgsConstructor
public class BlockAckPayload {
    private final Prenotazione blocco;
    private final Long aulaId;
    private final String periodo;
    private final Long amministratore;
}
