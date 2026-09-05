package com.prenotazioni.booking.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entità Prenotazione - Basata su analisi frontend
 * Campi utilizzati dal frontend (normalizeBookingData):
 * - id, aulaId/roomId, corsoId/courseId, utenteId/userId
 * - inizio/startTime, fine/endTime, stato/status
 * - descrizione/description, dataCreazione/createdAt
 * - nomeAula/roomName, nomeCorso/courseName (calcolati via JOIN)
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = true) // Nullable per blocchi admin
    private Course course;
    
    // Istantanea e non relazione: gli utenti vivono in un altro servizio.
    // Vedi ProprietarioPrenotazione per il perche' e per le conseguenze.
    @Embedded
    private BookingOwner user;
    
    @Column(nullable = false)
    private LocalDateTime startTime;
    
    @Column(nullable = false)
    private LocalDateTime endTime;
    
    // Persistito come stringa minuscola dal converter di StatoPrenotazione, per restare
    // compatibile con il CHECK constraint prenotazione_stato_check e col frontend.
    @Column(nullable = false, length = 20)
    private BookingStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = BookingStatus.BOOKED;
        }
    }
}
