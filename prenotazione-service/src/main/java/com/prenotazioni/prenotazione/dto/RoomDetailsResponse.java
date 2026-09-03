package com.prenotazioni.prenotazione.dto;

import com.prenotazioni.prenotazione.model.DisponibilitaAula;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class RoomDetailsResponse {
    private Long id;
    private String name;
    private int floor;
    private int capacity;
    private boolean isVirtual;
    // Enum e non String: @JsonValue lo serializza nel valore minuscolo di prima,
    // quindi il JSON resta identico, ma i valori possibili sono ora un insieme chiuso.
    private DisponibilitaAula status;
    private CurrentBooking booking;
    private BlockInfo blocked;
    private List<BookingInfo> bookings;

    public RoomDetailsResponse(Long id, String name, int floor, int capacity, boolean isVirtual) {
        this.id = id;
        this.name = name;
        this.floor = floor;
        this.capacity = capacity;
        this.isVirtual = isVirtual;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentBooking {
        private String user;
        private String date;
        private String time;
        private String purpose;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockInfo {
        private String reason;
        private String blockedBy;
        private String blockedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingInfo {
        private String date;
        private String startTime;
        private String endTime;
        private String user;
        private String purpose;
    }
}
