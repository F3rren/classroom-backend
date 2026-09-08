package com.classroom.booking.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A booking of a room.
 *
 * The fields a client reads: id, roomId, courseId, userId, startTime, endTime, status,
 * description, createdAt, plus roomName and courseName, which are computed through a JOIN
 * rather than stored here.
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
    @JoinColumn(name = "course_id", nullable = true) // null for an admin block
    private Course course;
    
    // A snapshot and not a relation: users live in another service.
    // See BookingOwner for the reasoning and the consequences.
    @Embedded
    private BookingOwner user;
    
    @Column(nullable = false)
    private LocalDateTime startTime;
    
    @Column(nullable = false)
    private LocalDateTime endTime;
    
    // Stored as a lowercase string by BookingStatus's converter, to stay compatible with
    // the booking_status_check CHECK constraint and with the client.
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
