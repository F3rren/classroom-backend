package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Wrapper non avvolto in ApiEnvelope per GET /api/prenotazioni/{id} (shape gia' esistente). */
@Getter
@AllArgsConstructor
public class PrenotazioneWrapper {
    private final Prenotazione prenotazione;
}
