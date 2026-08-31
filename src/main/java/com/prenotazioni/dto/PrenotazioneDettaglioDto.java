package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Vista "dettaglio completo" di una prenotazione (join aula/utente/corso), popolata
 * direttamente dalla query JPQL "new" in IPrenotazioneRepository invece di una Map generica.
 * L'ordine dei campi deve combaciare esattamente con l'ordine delle colonne nella SELECT new
 * JPQL, perche' il costruttore generato da @AllArgsConstructor segue l'ordine di dichiarazione.
 * corsoId/corsoNome/docente sono null per prenotazioni senza corso associato (blocchi/manutenzione admin).
 */
@Getter
@AllArgsConstructor
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
}
