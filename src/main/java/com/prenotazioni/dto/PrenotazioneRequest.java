package com.prenotazioni.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Richiesta di creazione/modifica/blocco di una prenotazione.
 * inizio/fine restano String (formato "2024-12-25T14:30:00"): il parsing e i controlli
 * di range (fine dopo inizio, non nel passato, ecc.) sono logica applicativa fatta
 * a mano nel controller dopo che questa validazione di presenza campo e' passata.
 */
public class PrenotazioneRequest {

    @NotNull(message = "Devi specificare quale aula vuoi prenotare.")
    private Long aulaId;

    private Long corsoId;

    @NotBlank(message = "Devi specificare quando inizia la prenotazione.")
    private String inizio;

    @NotBlank(message = "Devi specificare quando finisce la prenotazione.")
    private String fine;

    private String descrizione;

    public Long getAulaId() { return aulaId; }
    public void setAulaId(Long aulaId) { this.aulaId = aulaId; }
    public Long getCorsoId() { return corsoId; }
    public void setCorsoId(Long corsoId) { this.corsoId = corsoId; }
    public String getInizio() { return inizio; }
    public void setInizio(String inizio) { this.inizio = inizio; }
    public String getFine() { return fine; }
    public void setFine(String fine) { this.fine = fine; }
    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
}
