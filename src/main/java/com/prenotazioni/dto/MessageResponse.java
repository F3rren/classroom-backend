package com.prenotazioni.dto;

import lombok.Value;

/** Risposta minimale con un solo messaggio, per operazioni di scrittura senza payload di rilievo. */
@Value
public class MessageResponse {
    String message;
}
