package com.classroom.booking.dto;

import com.classroom.booking.model.RoomAvailability;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RoomDetailsResponse(
        Long id,
        String name,
        int floor,
        int capacity,
        // Lombok's boolean-getter convention (isVirtual() strips the "is") already made this
        // field serialise as "virtual", not "isVirtual" - confirmed by printing the actual
        // JSON before this conversion. A record has no such convention: without this
        // annotation the property would become "isVirtual" and silently change the API.
        @JsonProperty("virtual")
        boolean isVirtual,
        // An enum and not a String: @JsonValue serialises it to the same lowercase value as
        // before, so the JSON is unchanged, but the possible values are now a closed set.
        RoomAvailability status,
        CurrentBooking booking,
        BlockInfo blocked,
        List<BookingInfo> bookings) {

    public RoomDetailsResponse(Long id, String name, int floor, int capacity, boolean isVirtual) {
        this(id, name, floor, capacity, isVirtual, null, null, null, null);
    }

    public record CurrentBooking(String user, String date, String time, String purpose) {
    }

    public record BlockInfo(String reason, String blockedBy, String blockedAt) {
    }

    public record BookingInfo(String date, String startTime, String endTime, String user, String purpose) {
    }
}
