package com.classroom.booking.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A room that can be booked.
 *
 * The fields the frontend reads (normalizeRoomData): id, name, capacity, floor,
 * isVirtual, description, status.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    /**
     * Optimistic locking. Hibernate bumps it on every update and refuses one whose version
     * has moved on, which is what turns two people editing the same row at once from "the
     * later write wins in silence" into a 409 for the one who lost.
     *
     * Never set by hand: it is Hibernate's, and the DTOs deliberately do not carry it.
     *
     * Left out of equals/hashCode/toString: it changes on every update, so including it would
     * make an entity's identity - and its logged shape - drift across its own lifecycle.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, unique = true, length = 100)
    @ToString.Include
    private String name;

    @Column(nullable = false)
    @ToString.Include
    private int capacity;

    @Column(nullable = false)
    @ToString.Include
    private int floor;

    @Column(name = "is_virtual", nullable = false)
    @JsonProperty("isVirtual")
    @ToString.Include
    private boolean isVirtual = false;

    @Column(columnDefinition = "TEXT")
    @ToString.Include
    private String description;

    @Column(length = 20)
    // Stored lowercase by RoomStatus's converter (CHECK constraint room_status_check)
    @ToString.Include
    private RoomStatus status;

    @PrePersist
    @PreUpdate
    protected void setDefaults() {
        if (status == null) {
            status = RoomStatus.FREE;
        }
    }
}
