package com.prenotazioni.prenotazione.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Richiesta di creazione/modifica/blocco di una prenotazione.
 * inizio/fine restano String (formato "2024-12-25T14:30:00"): il parsing e i controlli
 * di range (fine dopo inizio, non nel passato, ecc.) sono logica applicativa fatta
 * a mano nel controller dopo che questa validazione di presenza campo e' passata.
 */
@Data
@Schema(description = "Dati di una prenotazione, usati per crearla, modificarla o bloccare un'aula")
public class PrenotazioneRequest {

    @NotNull(message = "Devi specificare quale aula vuoi prenotare.")
    @Schema(description = "ID dell'aula da prenotare", example = "3")
    private Long aulaId;

    @Schema(description = "ID del corso associato. Opzionale: assente per prenotazioni libere", example = "12")
    private Long corsoId;

    @NotBlank(message = "Devi specificare quando inizia la prenotazione.")
    @Schema(description = "Inizio della prenotazione, formato ISO senza fuso orario. Deve essere nel futuro",
            example = "2026-12-25T14:30:00")
    private String inizio;

    @NotBlank(message = "Devi specificare quando finisce la prenotazione.")
    @Schema(description = "Fine della prenotazione, deve essere successiva a inizio",
            example = "2026-12-25T16:30:00")
    private String fine;

    @Schema(description = "Descrizione libera, mostrata nei dettagli aula", example = "Lezione di Analisi 1")
    private String descrizione;
}
