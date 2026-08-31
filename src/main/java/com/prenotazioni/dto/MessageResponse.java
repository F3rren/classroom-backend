package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Risposta minimale con un solo messaggio, per operazioni di scrittura senza payload di rilievo. */
@Getter
@AllArgsConstructor
public class MessageResponse {
    private final String message;
}
