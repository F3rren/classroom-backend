package com.prenotazioni.dto;

import lombok.Value;

/** Risposta con un solo contatore, es. numero di notifiche non lette. */
@Value
public class CountResponse {
    long count;
}
