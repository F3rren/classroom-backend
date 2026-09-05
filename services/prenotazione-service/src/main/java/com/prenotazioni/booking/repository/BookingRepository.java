package com.prenotazioni.booking.repository;

import com.prenotazioni.booking.dto.BookingDetailDto;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    // Trova prenotazioni che si sovrappongono con un periodo dato
    @Query("SELECT p FROM Booking p WHERE p.aula.id = :aulaId " +
           "AND p.stato != 'annullata' " +
           "AND ((p.inizio <= :inizio AND p.fine > :inizio) " +
           "OR (p.inizio < :fine AND p.fine >= :fine) " +
           "OR (p.inizio >= :inizio AND p.fine <= :fine))")
    List<Booking> findConflittingReservations(@Param("aulaId") Long aulaId, 
                                                   @Param("inizio") LocalDateTime inizio, 
                                                   @Param("fine") LocalDateTime fine);

    // Trova prenotazioni che si sovrappongono con un periodo dato escludendo una prenotazione specifica
    @Query("SELECT p FROM Booking p WHERE p.aula.id = :aulaId " +
           "AND p.stato != 'annullata' " +
           "AND p.id != :prenotazioneIdEsclusa " +
           "AND ((p.inizio <= :inizio AND p.fine > :inizio) " +
           "OR (p.inizio < :fine AND p.fine >= :fine) " +
           "OR (p.inizio >= :inizio AND p.fine <= :fine))")
    List<Booking> findConflittingReservationsExcluding(@Param("aulaId") Long aulaId, 
                                                           @Param("inizio") LocalDateTime inizio, 
                                                           @Param("fine") LocalDateTime fine,
                                                           @Param("prenotazioneIdEsclusa") Long prenotazioneIdEsclusa);
    
    // Trova prenotazioni attive in un momento specifico
    @Query("SELECT p FROM Booking p WHERE p.aula.id = :aulaId " +
           "AND p.stato != 'annullata' " +
           "AND p.inizio <= :momento AND p.fine > :momento " +
           "ORDER BY p.stato DESC") // MANUTENZIONE, BLOCCATA, PRENOTATA
    List<Booking> findActiveReservations(@Param("aulaId") Long aulaId,
                                             @Param("momento") LocalDateTime momento);
    
    // Trova prenotazioni per utente
    @Query("SELECT p FROM Booking p WHERE p.utente.id = :utenteId " +
           "ORDER BY p.inizio DESC")
    List<Booking> findByUtenteId(@Param("utenteId") Long utenteId);
    
    // Trova prenotazioni per stato
    List<Booking> findByStato(BookingStatus stato);
    
    // Trova prenotazioni future
    @Query("SELECT p FROM Booking p WHERE p.inizio > :ora AND p.stato != 'annullata' " +
           "ORDER BY p.inizio ASC")
    List<Booking> findPrenotazioniFuture(@Param("ora") LocalDateTime ora);
    
    // Vista completa prenotazioni per una specifica aula
    @Query("SELECT new com.prenotazioni.booking.dto.BookingDetailDto(" +
           "p.id, " +
           "p.inizio, " +
           "p.fine, " +
           "p.stato, " +
           "p.descrizione, " +
           "p.dataCreazione, " +
           "a.id, " +
           "a.nome, " +
           "a.capienza, " +
           "a.piano, " +
           "u.id, " +
           "u.username, " +
           "u.nome, " +
           "c.id, " +
           "c.nome, " +
           "c.docente, " +
           "CASE WHEN p.inizio > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.fine < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END) " +
           "FROM Booking p " +
           "JOIN p.aula a " +
           "JOIN p.utente u " +
           "LEFT JOIN p.corso c " +
           "WHERE a.id = :aulaId " +
           "ORDER BY p.inizio DESC")
    List<BookingDetailDto> findCompleteDetailsByAulaId(@Param("aulaId") Long aulaId);
    
    // Vista completa di tutte le prenotazioni
    @Query("SELECT new com.prenotazioni.booking.dto.BookingDetailDto(" +
           "p.id, " +
           "p.inizio, " +
           "p.fine, " +
           "p.stato, " +
           "p.descrizione, " +
           "p.dataCreazione, " +
           "a.id, " +
           "a.nome, " +
           "a.capienza, " +
           "a.piano, " +
           "u.id, " +
           "u.username, " +
           "u.nome, " +
           "c.id, " +
           "c.nome, " +
           "c.docente, " +
           "CASE WHEN p.inizio > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.fine < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END) " +
           "FROM Booking p " +
           "JOIN p.aula a " +
           "JOIN p.utente u " +
           "LEFT JOIN p.corso c " +
           "ORDER BY p.inizio DESC")
    List<BookingDetailDto> findAllCompleteDetails();
    
    // Dettagli completi per una singola prenotazione
    @Query("SELECT new com.prenotazioni.booking.dto.BookingDetailDto(" +
           "p.id, " +
           "p.inizio, " +
           "p.fine, " +
           "p.stato, " +
           "p.descrizione, " +
           "p.dataCreazione, " +
           "a.id, " +
           "a.nome, " +
           "a.capienza, " +
           "a.piano, " +
           "u.id, " +
           "u.username, " +
           "u.nome, " +
           "c.id, " +
           "c.nome, " +
           "c.docente, " +
           "CASE WHEN p.inizio > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.fine < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END) " +
           "FROM Booking p " +
           "JOIN p.aula a " +
           "JOIN p.utente u " +
           "LEFT JOIN p.corso c " +
           "WHERE p.id = :prenotazioneId")
    List<BookingDetailDto> findCompleteDetailsByPrenotazioneId(@Param("prenotazioneId") Long prenotazioneId);
    
    // Trova tutte le prenotazioni per una specifica aula
    @Query("SELECT p FROM Booking p WHERE p.aula.id = :aulaId ORDER BY p.inizio ASC")
    List<Booking> findByAulaId(@Param("aulaId") Long aulaId);

    /**
     * Le prenotazioni di piu' aule in una query sola, per costruire l'elenco dei
     * dettagli senza interrogare il database una volta per aula.
     *
     * Le tre relazioni di Prenotazione sono tutte EAGER, quindi senza i JOIN FETCH
     * l'N+1 si limiterebbe a spostarsi: ogni riga caricata ne farebbe scattare altre
     * per aula, utente e corso. Su corso il join e' LEFT perche' e' nullable (i blocchi
     * admin non hanno corso): un JOIN FETCH normale li escluderebbe silenziosamente
     * dal risultato, e le aule bloccate risulterebbero libere.
     *
     * L'ordinamento per inizio ASC ricalca findByAulaId: i cicli che consumano questa
     * lista si fermano alla prima prenotazione utile, quindi l'ordine e' significativo.
     */
    @Query("SELECT p FROM Booking p " +
           "JOIN FETCH p.aula a " +
           "JOIN FETCH p.utente " +
           "LEFT JOIN FETCH p.corso " +
           "WHERE a.id IN :aulaIds ORDER BY p.inizio ASC")
    List<Booking> findByAulaIdIn(@Param("aulaIds") List<Long> aulaIds);
    
    // Elimina tutte le prenotazioni di un utente (per eliminazione utente)
    @Modifying
    @Query("DELETE FROM Booking p WHERE p.utente.id = :utenteId")
    void deleteByUtenteId(@Param("utenteId") Long utenteId);
}
