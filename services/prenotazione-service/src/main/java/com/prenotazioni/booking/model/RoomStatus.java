package com.prenotazioni.booking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * The STORED state of a room, meaning the room.status column on disk.
 *
 * Not to be confused with the two UPPERCASE vocabularies computed at runtime, which are
 * different contracts and stay plain strings:
 *  - {@code BookingService.getRoomStatus()} returns FREE/MAINTENANCE/BLOCKED/BOOKED
 *    (note BOOKED, which does not exist here, and no BUSY);
 *  - {@code AvailabilityPayload.status} returns FREE/BUSY.
 * Three overlapping vocabularies is one more than anybody needs, but merging them is a
 * change of contract rather than a refactor, so it is left as its own decision.
 *
 * There is also {@link RoomAvailability}, the "status" field of RoomDetailsResponse:
 * lowercase like this enum but with BOOKED and without BUSY, so that one is not
 * interchangeable with this either.
 *
 * As with {@link BookingStatus}, the value on disk and in JSON is lowercase and separate
 * from the constant name: room_status_check admits exactly these four.
 */
public enum RoomStatus {

    FREE("free"),
    BUSY("busy"),
    BLOCKED("blocked"),
    MAINTENANCE("maintenance");

    private final String value;

    RoomStatus(String value) {
        this.value = value;
    }

    /** The value used in JSON and in the database. */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Accepts any mix of upper and lower case, for the same reason as BookingStatus. */
    @JsonCreator
    public static RoomStatus from(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (RoomStatus status : values()) {
            if (status.value.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid room status: " + value);
    }

    /**
     * JPA converter: writes and reads the lowercase value, not the constant name.
     * autoApply so that no field needs annotating.
     */
    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<RoomStatus, String> {

        @Override
        public String convertToDatabaseColumn(RoomStatus status) {
            return status == null ? null : status.getValue();
        }

        @Override
        public RoomStatus convertToEntityAttribute(String value) {
            return from(value);
        }
    }
}
