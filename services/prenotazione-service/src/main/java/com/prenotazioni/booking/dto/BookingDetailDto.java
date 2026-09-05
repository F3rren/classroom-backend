package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.BookingStatus;
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
public class BookingDetailDto {

    Long bookingId;
    LocalDateTime startTime;
    LocalDateTime endTime;
    BookingStatus status;
    String notePrenotazione;
    LocalDateTime createdAt;
    Long roomId;
    String aulaNome;
    Integer aulaCapienza;
    Integer aulaPiano;
    Long userId;
    String username;
    String utenteNome;
    Long courseId;
    String corsoNome;
    String teacher;
    String statoTemporale;
}
