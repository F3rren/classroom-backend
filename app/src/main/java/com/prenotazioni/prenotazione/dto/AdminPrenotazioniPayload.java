package com.prenotazioni.prenotazione.dto;

import com.prenotazioni.prenotazione.model.Prenotazione;
import lombok.Value;

import java.util.List;

/** Vista admin di tutte le prenotazioni (incluse annullate), con statistiche per stato. */
@Value
public class AdminPrenotazioniPayload {
    List<Prenotazione> prenotazioni;
    PrenotazioniStats statistiche;
}
