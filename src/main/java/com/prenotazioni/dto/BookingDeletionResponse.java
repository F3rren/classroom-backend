package com.prenotazioni.dto;

/** Conferma di eliminazione prenotazione da parte di un admin. */
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

    public Long getDeletedBookingId() { return deletedBookingId; }
    public Long getAdminId() { return adminId; }
    public boolean isAdminAction() { return adminAction; }
    public String getReason() { return reason; }
}
