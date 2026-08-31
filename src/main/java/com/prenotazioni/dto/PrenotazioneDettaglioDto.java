package com.prenotazioni.dto;

import java.time.LocalDateTime;

/**
 * Vista "dettaglio completo" di una prenotazione (join aula/utente/corso), popolata
 * direttamente dalla query JPQL "new" in IPrenotazioneRepository invece di una Map generica.
 * L'ordine dei parametri del costruttore deve combaciare esattamente con l'ordine delle
 * colonne nella SELECT new JPQL. corsoId/corsoNome/docente sono null per prenotazioni senza
 * corso associato (blocchi/manutenzione admin).
 */
public class PrenotazioneDettaglioDto {

    private final Long prenotazioneId;
    private final LocalDateTime inizio;
    private final LocalDateTime fine;
    private final String stato;
    private final String notePrenotazione;
    private final LocalDateTime dataCreazione;
    private final Long aulaId;
    private final String aulaNome;
    private final Integer aulaCapienza;
    private final Integer aulaPiano;
    private final Long utenteId;
    private final String username;
    private final String utenteNome;
    private final Long corsoId;
    private final String corsoNome;
    private final String docente;
    private final String statoTemporale;

    public PrenotazioneDettaglioDto(Long prenotazioneId, LocalDateTime inizio, LocalDateTime fine, String stato,
                                     String notePrenotazione, LocalDateTime dataCreazione, Long aulaId, String aulaNome,
                                     Integer aulaCapienza, Integer aulaPiano, Long utenteId, String username,
                                     String utenteNome, Long corsoId, String corsoNome, String docente,
                                     String statoTemporale) {
        this.prenotazioneId = prenotazioneId;
        this.inizio = inizio;
        this.fine = fine;
        this.stato = stato;
        this.notePrenotazione = notePrenotazione;
        this.dataCreazione = dataCreazione;
        this.aulaId = aulaId;
        this.aulaNome = aulaNome;
        this.aulaCapienza = aulaCapienza;
        this.aulaPiano = aulaPiano;
        this.utenteId = utenteId;
        this.username = username;
        this.utenteNome = utenteNome;
        this.corsoId = corsoId;
        this.corsoNome = corsoNome;
        this.docente = docente;
        this.statoTemporale = statoTemporale;
    }

    public Long getPrenotazioneId() { return prenotazioneId; }
    public LocalDateTime getInizio() { return inizio; }
    public LocalDateTime getFine() { return fine; }
    public String getStato() { return stato; }
    public String getNotePrenotazione() { return notePrenotazione; }
    public LocalDateTime getDataCreazione() { return dataCreazione; }
    public Long getAulaId() { return aulaId; }
    public String getAulaNome() { return aulaNome; }
    public Integer getAulaCapienza() { return aulaCapienza; }
    public Integer getAulaPiano() { return aulaPiano; }
    public Long getUtenteId() { return utenteId; }
    public String getUsername() { return username; }
    public String getUtenteNome() { return utenteNome; }
    public Long getCorsoId() { return corsoId; }
    public String getCorsoNome() { return corsoNome; }
    public String getDocente() { return docente; }
    public String getStatoTemporale() { return statoTemporale; }
}
