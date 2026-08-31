package com.prenotazioni.dto;

/** Risposta minimale con un solo messaggio, per operazioni di scrittura senza payload di rilievo. */
public class MessageResponse {
    private final String message;

    public MessageResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
