package com.prenotazioni.prenotazione.dto;

import lombok.Getter;

/** Conferma di eliminazione prenotazione da parte di un admin. */
@Getter
public class BookingDeletionResponse {
    private final Long deletedBookingId;
    private final Long adminId;
    private final boolean adminAction = true;
    private final String reason;

    public BookingDeletionResponse(Long deletedBookingId, Long adminId, String reason) {
        this.deletedBookingId = deletedBookingId;
        this.adminId = adminId;
        this.reason = reason;
    }
}
