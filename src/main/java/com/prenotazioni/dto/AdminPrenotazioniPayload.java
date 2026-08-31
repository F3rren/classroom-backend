package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** Vista admin di tutte le prenotazioni (incluse annullate), con statistiche per stato. */
@Getter
@AllArgsConstructor
public class AdminPrenotazioniPayload {
    private final List<Prenotazione> prenotazioni;
    private final PrenotazioniStats statistiche;
}
