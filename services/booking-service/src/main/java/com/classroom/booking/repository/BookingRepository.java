package com.classroom.booking.repository;

import com.classroom.booking.dto.BookingDetailDto;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    // The bookings that overlap a given period.
    @Query("SELECT p FROM Booking p WHERE p.room.id = :roomId " +
           "AND p.status != 'cancelled' " +
           "AND ((p.startTime <= :start AND p.endTime > :start) " +
           "OR (p.startTime < :end AND p.endTime >= :end) " +
           "OR (p.startTime >= :start AND p.endTime <= :end))")
    List<Booking> findConflictingBookings(@Param("roomId") Long roomId, 
                                                   @Param("start") LocalDateTime startTime, 
                                                   @Param("end") LocalDateTime endTime);

    // The bookings that overlap a given period, ignoring one particular booking.
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
    
    // The bookings active at one specific moment.
    @Query("SELECT p FROM Booking p WHERE p.room.id = :roomId " +
           "AND p.status != 'cancelled' " +
           "AND p.startTime <= :moment AND p.endTime > :moment " +
           "ORDER BY p.status DESC") // only for a stable order: the caller decides the precedence
    List<Booking> findActiveBookings(@Param("roomId") Long roomId,
                                             @Param("moment") LocalDateTime moment);
    
    // The bookings of one user.
    @Query("SELECT p FROM Booking p WHERE p.user.id = :userId " +
           "ORDER BY p.startTime DESC")
    List<Booking> findByUserId(@Param("userId") Long userId);
    
    // The bookings in a given status.
    List<Booking> findByStatus(BookingStatus status);
    
    // Trova prenotazioni future
    @Query("SELECT p FROM Booking p WHERE p.startTime > :now AND p.status != 'cancelled' " +
           "ORDER BY p.startTime ASC")
    List<Booking> findFutureBookings(@Param("now") LocalDateTime now);
    
    // The full booking view for one particular room.
    @Query("SELECT new com.classroom.booking.dto.BookingDetailDto(" +
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
    
    // The full booking view, for every booking.
    @Query("SELECT new com.classroom.booking.dto.BookingDetailDto(" +
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
    
    // Full details for a single booking.
    @Query("SELECT new com.classroom.booking.dto.BookingDetailDto(" +
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
    
    // Every booking for one particular room.
    @Query("SELECT p FROM Booking p WHERE p.room.id = :roomId ORDER BY p.startTime ASC")
    List<Booking> findByRoomId(@Param("roomId") Long roomId);

    /**
     * The bookings of several rooms in a single query, so the detail listing can be built
     * without asking the database once per room.
     *
     * Booking's three relations are all EAGER, so without the JOIN FETCHes the N+1 would
     * merely move: every row loaded would trigger further ones for the room, the user and
     * the course. The join on the course is a LEFT one because it is nullable (admin blocks
     * have no course): a plain JOIN FETCH would silently drop them from the result, and
     * blocked rooms would come out as free.
     *
     * Ordering by start_time ASC mirrors findByRoomId: the loops consuming this list stop at
     * the first useful booking, so the order carries meaning.
     */
    @Query("SELECT p FROM Booking p " +
           "JOIN FETCH p.room a " +
           "JOIN FETCH p.user " +
           "LEFT JOIN FETCH p.course " +
           "WHERE a.id IN :roomIds ORDER BY p.startTime ASC")
    List<Booking> findByRoomIdIn(@Param("roomIds") List<Long> roomIds);
    
    // Deletes every booking of a user, for when the user itself is deleted.
    @Modifying
    @Query("DELETE FROM Booking p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
