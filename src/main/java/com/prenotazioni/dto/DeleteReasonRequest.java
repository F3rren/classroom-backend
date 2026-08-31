package com.prenotazioni.dto;

/** Corpo opzionale per DELETE /api/admin/prenotazioni/{id}: motivo dell'eliminazione forzata. */
public class DeleteReasonRequest {
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
