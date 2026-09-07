package com.classroom.booking.dto;

import lombok.Value;

/** The success response for DELETE /api/bookings/{id}. */
@Value
public class CancellationAckPayload {
    Long bookingId;
    Long userId;
    String dataAnnullamento;
}
