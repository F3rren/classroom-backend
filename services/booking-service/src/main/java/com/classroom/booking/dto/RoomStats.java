package com.classroom.booking.dto;

/** Physical versus virtual room statistics, inside RoomStatsPayload.statistics. */
public record RoomStats(
        long totalRooms,
        long physicalRooms,
        long virtualRooms,
        double physicalPercentage,
        double virtualPercentage,
        boolean hasRooms) {

    public RoomStats(long physicalRooms, long virtualRooms) {
        this(physicalRooms + virtualRooms, physicalRooms, virtualRooms,
                percentage(physicalRooms, physicalRooms + virtualRooms),
                percentage(virtualRooms, physicalRooms + virtualRooms),
                (physicalRooms + virtualRooms) > 0);
    }

    private static double percentage(long part, long total) {
        return total > 0 ? Math.round((double) part / total * 10000.0) / 100.0 : 0.0;
    }
}
