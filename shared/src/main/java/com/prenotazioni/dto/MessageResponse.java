package com.prenotazioni.dto;

import lombok.Value;

/** A minimal response carrying one message, for writes with no payload worth returning. */
@Value
public class MessageResponse {
    String message;
}
