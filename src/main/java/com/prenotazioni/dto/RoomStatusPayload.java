package com.prenotazioni.dto;

import lombok.Value;

import java.time.LocalDateTime;

/** Risposta non avvolta di GET /api/prenotazioni/stato/{aulaId} (comportamento gia' esistente). */
@Value
public class RoomStatusPayload {
    Long aulaId;
    String stato;
    LocalDateTime timestamp;
}
