package com.prenotazioni.dto;

import lombok.Value;

/** A response carrying a single count, the number of unread notifications for instance. */
@Value
public class CountResponse {
    long count;
}
