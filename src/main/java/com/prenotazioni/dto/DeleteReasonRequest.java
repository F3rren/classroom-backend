package com.prenotazioni.dto;

import lombok.Data;

/** Corpo opzionale per DELETE /api/admin/prenotazioni/{id}: motivo dell'eliminazione forzata. */
@Data
public class DeleteReasonRequest {
    private String reason;
}
