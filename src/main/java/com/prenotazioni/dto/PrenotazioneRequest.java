package com.prenotazioni.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Richiesta di creazione/modifica/blocco di una prenotazione.
 * inizio/fine restano String (formato "2024-12-25T14:30:00"): il parsing e i controlli
 * di range (fine dopo inizio, non nel passato, ecc.) sono logica applicativa fatta
 * a mano nel controller dopo che questa validazione di presenza campo e' passata.
 */
@Data
public class PrenotazioneRequest {

    @NotNull(message = "Devi specificare quale aula vuoi prenotare.")
    private Long aulaId;

    private Long corsoId;

    @NotBlank(message = "Devi specificare quando inizia la prenotazione.")
    private String inizio;

    @NotBlank(message = "Devi specificare quando finisce la prenotazione.")
    private String fine;

    private String descrizione;
}
