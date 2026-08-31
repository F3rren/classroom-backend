package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Risposta non avvolta di GET /api/prenotazioni/stato/{aulaId} (comportamento gia' esistente). */
@Getter
@AllArgsConstructor
public class RoomStatusPayload {
    private final Long aulaId;
    private final String stato;
    private final LocalDateTime timestamp;
}
