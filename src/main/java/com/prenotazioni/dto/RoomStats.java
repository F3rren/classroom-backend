package com.prenotazioni.dto;

/** Statistiche aule fisiche vs virtuali, dentro RoomStatsPayload.statistics. */
public class RoomStats {
    private final long totalRooms;
    private final long physicalRooms;
    private final long virtualRooms;
    private final double physicalPercentage;
    private final double virtualPercentage;
    private final boolean hasRooms;

    public RoomStats(long physicalRooms, long virtualRooms) {
        this.physicalRooms = physicalRooms;
        this.virtualRooms = virtualRooms;
        this.totalRooms = physicalRooms + virtualRooms;
        this.physicalPercentage = totalRooms > 0 ? Math.round((double) physicalRooms / totalRooms * 10000.0) / 100.0 : 0.0;
        this.virtualPercentage = totalRooms > 0 ? Math.round((double) virtualRooms / totalRooms * 10000.0) / 100.0 : 0.0;
        this.hasRooms = totalRooms > 0;
    }

    public long getTotalRooms() { return totalRooms; }
    public long getPhysicalRooms() { return physicalRooms; }
    public long getVirtualRooms() { return virtualRooms; }
    public double getPhysicalPercentage() { return physicalPercentage; }
    public double getVirtualPercentage() { return virtualPercentage; }
    public boolean isHasRooms() { return hasRooms; }
}
