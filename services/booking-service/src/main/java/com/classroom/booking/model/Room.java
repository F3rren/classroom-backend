package com.classroom.booking.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A room that can be booked.
 *
 * The fields the frontend reads (normalizeRoomData): id, name, capacity, floor,
 * isVirtual, description, status.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "rooms")
public class Room {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic locking. Hibernate bumps it on every update and refuses one whose version
     * has moved on, which is what turns two people editing the same row at once from "the
     * later write wins in silence" into a 409 for the one who lost.
     *
     * Never set by hand: it is Hibernate's, and the DTOs deliberately do not carry it.
     */
    @Version
    @Column(nullable = false)
    private Long version;
    
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    
    @Column(nullable = false)
    private int capacity;
    
    @Column(nullable = false)
    private int floor;
    
    @Column(name = "is_virtual", nullable = false)
    @JsonProperty("isVirtual")
    private boolean isVirtual = false;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(length = 20)
    // Stored lowercase by RoomStatus's converter (CHECK constraint room_status_check)
    private RoomStatus status;
    
    @PrePersist
    @PreUpdate
    protected void setDefaults() {
        if (status == null) {
            status = RoomStatus.FREE;
        }
    }
}
