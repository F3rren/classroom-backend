package com.prenotazioni.booking.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The COMPUTED availability of a room at a given instant, meaning the "status" field of
 * RoomDetailsResponse. It is not persisted: it is derived every time from the bookings
 * that cross that moment.
 *
 * It is a vocabulary of its own rather than a reuse of {@link RoomStatus}: that one has
 * no BOOKED, which here is the most frequent case. Before this enum it was raw strings
 * repeated in three copies of the same block.
 *
 * The values are lowercase and {@link JsonValue} serialises them as such. No
 * AttributeConverter is needed, unlike the persisted enums, because this value never
 * reaches the database.
 */
public enum RoomAvailability {

    FREE("free"),
    BOOKED("booked"),
    BLOCKED("blocked");

    private final String value;

    RoomAvailability(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
