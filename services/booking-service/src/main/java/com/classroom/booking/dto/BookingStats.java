package com.classroom.booking.dto;

import lombok.Value;

/** The per-status booking counts, inside AdminBookingsPayload. */
@Value
public class BookingStats {
    long total;
    long active;
    long cancelled;
}
