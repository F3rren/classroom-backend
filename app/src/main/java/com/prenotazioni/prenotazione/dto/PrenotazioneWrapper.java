package com.prenotazioni.prenotazione.dto;

import com.prenotazioni.prenotazione.model.Prenotazione;
import lombok.Value;

/** Wrapper non avvolto in ApiEnvelope per GET /api/prenotazioni/{id} (shape gia' esistente). */
@Value
public class PrenotazioneWrapper {
    Prenotazione prenotazione;
}
