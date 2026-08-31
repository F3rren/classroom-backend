package com.prenotazioni.dto;

/** Risposta con un solo contatore, es. numero di notifiche non lette. */
public class CountResponse {
    private final long count;

    public CountResponse(long count) {
        this.count = count;
    }

    public long getCount() {
        return count;
    }
}
