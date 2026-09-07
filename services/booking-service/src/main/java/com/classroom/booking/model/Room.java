package com.classroom.booking.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A room that can be booked.
 * Campi utilizzati dal frontend (normalizeRoomData):
 * - id, name, capacity, floor, isVirtual, description, status/status
 * 
 * Il frontend normalizza i dati quindi possiamo usare nomi italiani
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
