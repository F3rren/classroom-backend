package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Risposta con un solo contatore, es. numero di notifiche non lette. */
@Getter
@AllArgsConstructor
public class CountResponse {
    private final long count;
}
