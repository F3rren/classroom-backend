package com.prenotazioni.booking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * State of a booking, once represented by loose strings scattered across some sixty
 * places in services, controllers and queries.
 *
 * THE EXTERNAL FORM IS LOWERCASE, in both directions:
 *  - towards the client: {@link JsonValue} serialises the value, NOT the constant name,
 *    so the wire carries "booked" rather than "BOOKED";
 *  - towards the database: the column has a CHECK constraint that admits exactly these
 *    values (booking_status_check), so persistence goes through the converter below
 *    instead of through {@code @Enumerated}, which would store the uppercase name.
 *
 * Keeping the name and the value as two separate things is what made the translation to
 * English cheap: the constants could be renamed with the compiler checking every call
 * site, and the values changed on their own schedule, in a migration that also had to
 * rewrite the rows and the CHECK constraint.
 */
public enum BookingStatus {

    BOOKED("booked"),
    /**
     * Admitted by the CHECK constraint and referred to historically, but no point in the
     * code assigns it: it is kept only so that a legacy row carrying this value can still
     * be read without failing deserialisation.
     */
    CONFIRMED("confirmed"),
    BLOCKED("blocked"),
    MAINTENANCE("maintenance"),
    CANCELLED("cancelled");

    private final String value;

    BookingStatus(String value) {
        this.value = value;
    }

    /** The value used in JSON and in the database. */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Accepts any mix of upper and lower case: before this enum existed the code compared
     * with equalsIgnoreCase, so data or requests in a different case have to keep working.
     */
    @JsonCreator
    public static BookingStatus from(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (BookingStatus status : values()) {
            if (status.value.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid booking status: " + value);
    }

    /** True when the booking is active, meaning the user can still cancel it. */
    public boolean isActive() {
        return this == BOOKED;
    }

    /** True for the states that hold the room because an admin decided so. */
    public boolean isAdminIntervention() {
        return this == BLOCKED || this == MAINTENANCE;
    }

    /**
     * JPA converter: writes and reads the lowercase value, not the constant name.
     * autoApply so that no field needs annotating.
     */
    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<BookingStatus, String> {

        @Override
        public String convertToDatabaseColumn(BookingStatus status) {
            return status == null ? null : status.getValue();
        }

        @Override
        public BookingStatus convertToEntityAttribute(String value) {
            return from(value);
        }
    }
}
