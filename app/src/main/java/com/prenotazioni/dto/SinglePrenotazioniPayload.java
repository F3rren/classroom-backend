package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.Value;

import java.util.List;

/**
 * Lista prenotazioni con la sola chiave "prenotazioni" (nessun "totalPrenotazioni"),
 * riusata da GET /mie e dal ramo non-vuoto di GET (lista base) - shape gia' esistente.
 */
@Value
public class SinglePrenotazioniPayload {
    List<Prenotazione> prenotazioni;
}
