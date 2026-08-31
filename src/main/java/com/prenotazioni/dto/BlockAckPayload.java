package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.Value;

/** Risposta di successo per il blocco aula da parte di un admin (POST /blocca). */
@Value
public class BlockAckPayload {
    Prenotazione blocco;
    Long aulaId;
    String periodo;
    Long amministratore;
}
