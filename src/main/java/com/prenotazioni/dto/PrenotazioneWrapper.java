package com.prenotazioni.dto;

import com.prenotazioni.model.Prenotazione;
import lombok.Value;

/** Wrapper non avvolto in ApiEnvelope per GET /api/prenotazioni/{id} (shape gia' esistente). */
@Value
public class PrenotazioneWrapper {
    Prenotazione prenotazione;
}
