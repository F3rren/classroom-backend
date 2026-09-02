package com.prenotazioni.dto;

import com.prenotazioni.model.StatoPrenotazione;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Vista "dettaglio completo" di una prenotazione (join aula/utente/corso), popolata
 * direttamente dalla query JPQL "new" in IPrenotazioneRepository invece di una Map generica.
 * L'ordine dei campi deve combaciare esattamente con l'ordine delle colonne nella SELECT new
 * JPQL, perche' il costruttore generato da @Value (equivalente ad @AllArgsConstructor) segue
 * l'ordine di dichiarazione. corsoId/corsoNome/docente sono null per prenotazioni senza corso
 * associato (blocchi/manutenzione admin).
 */
@Value
public class PrenotazioneDettaglioDto {

    Long prenotazioneId;
    LocalDateTime inizio;
    LocalDateTime fine;
    StatoPrenotazione stato;
    String notePrenotazione;
    LocalDateTime dataCreazione;
    Long aulaId;
    String aulaNome;
    Integer aulaCapienza;
    Integer aulaPiano;
    Long utenteId;
    String username;
    String utenteNome;
    Long corsoId;
    String corsoNome;
    String docente;
    String statoTemporale;
}
