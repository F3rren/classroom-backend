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
    @Query("SELECT p FROM Booking p WHERE p.room.id = :roomId " +
           "AND p.status != 'cancelled' " +
           "AND ((p.startTime <= :start AND p.endTime > :start) " +
           "OR (p.startTime < :end AND p.endTime >= :end) " +
           "OR (p.startTime >= :start AND p.endTime <= :end))")
    List<Booking> findConflictingBookings(@Param("roomId") Long roomId, 
                                                   @Param("start") LocalDateTime startTime, 
                                                   @Param("end") LocalDateTime endTime);

    // Trova prenotazioni che si sovrappongono con un periodo dato escludendo una prenotazione specifica
    @Query("SELECT p FROM Booking p WHERE p.room.id = :roomId " +
           "AND p.status != 'cancelled' " +
           "AND p.id != :excludedBookingId " +
           "AND ((p.startTime <= :start AND p.endTime > :start) " +
           "OR (p.startTime < :end AND p.endTime >= :end) " +
           "OR (p.startTime >= :start AND p.endTime <= :end))")
    List<Booking> findConflictingBookingsExcluding(@Param("roomId") Long roomId, 
                                                           @Param("start") LocalDateTime startTime, 
                                                           @Param("end") LocalDateTime endTime,
                                                           @Param("excludedBookingId") Long excludedBookingId);
    
    // Trova prenotazioni attive in un momento specifico
    @Query("SELECT p FROM Booking p WHERE p.room.id = :roomId " +
           "AND p.status != 'cancelled' " +
           "AND p.startTime <= :moment AND p.endTime > :moment " +
           "ORDER BY p.status DESC") // solo per un ordine stabile: la precedenza la decide il chiamante
    List<Booking> findActiveBookings(@Param("roomId") Long roomId,
                                             @Param("moment") LocalDateTime moment);
    
    // Trova prenotazioni per utente
    @Query("SELECT p FROM Booking p WHERE p.user.id = :userId " +
           "ORDER BY p.startTime DESC")
    List<Booking> findByUserId(@Param("userId") Long userId);
    
    // Trova prenotazioni per stato
    List<Booking> findByStatus(BookingStatus status);
    
    // Trova prenotazioni future
    @Query("SELECT p FROM Booking p WHERE p.startTime > :now AND p.status != 'cancelled' " +
           "ORDER BY p.startTime ASC")
    List<Booking> findFutureBookings(@Param("now") LocalDateTime now);
    
    // Vista completa prenotazioni per una specifica aula
    @Query("SELECT new com.prenotazioni.booking.dto.BookingDetailDto(" +
           "p.id, " +
           "p.startTime, " +
           "p.endTime, " +
           "p.status, " +
           "p.description, " +
           "p.createdAt, " +
           "a.id, " +
           "a.name, " +
           "a.capacity, " +
           "a.floor, " +
           "u.id, " +
           "u.username, " +
           "u.name, " +
           "c.id, " +
           "c.name, " +
           "c.teacher, " +
           "CASE WHEN p.startTime > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.endTime < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END) " +
           "FROM Booking p " +
           "JOIN p.room a " +
           "JOIN p.user u " +
           "LEFT JOIN p.course c " +
           "WHERE a.id = :roomId " +
           "ORDER BY p.startTime DESC")
    List<BookingDetailDto> findCompleteDetailsByRoomId(@Param("roomId") Long roomId);
    
    // Vista completa di tutte le prenotazioni
    @Query("SELECT new com.prenotazioni.booking.dto.BookingDetailDto(" +
           "p.id, " +
           "p.startTime, " +
           "p.endTime, " +
           "p.status, " +
           "p.description, " +
           "p.createdAt, " +
           "a.id, " +
           "a.name, " +
           "a.capacity, " +
           "a.floor, " +
           "u.id, " +
           "u.username, " +
           "u.name, " +
           "c.id, " +
           "c.name, " +
           "c.teacher, " +
           "CASE WHEN p.startTime > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.endTime < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END) " +
           "FROM Booking p " +
           "JOIN p.room a " +
           "JOIN p.user u " +
           "LEFT JOIN p.course c " +
           "ORDER BY p.startTime DESC")
    List<BookingDetailDto> findAllCompleteDetails();
    
    // Dettagli completi per una singola prenotazione
    @Query("SELECT new com.prenotazioni.booking.dto.BookingDetailDto(" +
           "p.id, " +
           "p.startTime, " +
           "p.endTime, " +
           "p.status, " +
           "p.description, " +
           "p.createdAt, " +
           "a.id, " +
           "a.name, " +
           "a.capacity, " +
           "a.floor, " +
           "u.id, " +
           "u.username, " +
           "u.name, " +
           "c.id, " +
           "c.name, " +
           "c.teacher, " +
           "CASE WHEN p.startTime > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.endTime < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END) " +
           "FROM Booking p " +
           "JOIN p.room a " +
           "JOIN p.user u " +
           "LEFT JOIN p.course c " +
           "WHERE p.id = :bookingId")
    List<BookingDetailDto> findCompleteDetailsByBookingId(@Param("bookingId") Long bookingId);
    
    // Trova tutte le prenotazioni per una specifica aula
    @Query("SELECT p FROM Booking p WHERE p.room.id = :roomId ORDER BY p.startTime ASC")
    List<Booking> findByRoomId(@Param("roomId") Long roomId);

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
           "JOIN FETCH p.room a " +
           "JOIN FETCH p.user " +
           "LEFT JOIN FETCH p.course " +
           "WHERE a.id IN :roomIds ORDER BY p.startTime ASC")
    List<Booking> findByRoomIdIn(@Param("roomIds") List<Long> roomIds);
    
    // Elimina tutte le prenotazioni di un utente (per eliminazione utente)
    @Modifying
    @Query("DELETE FROM Booking p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
