package com.classroom.booking.model;

import java.util.Collection;
import java.util.Optional;

/**
 * What is holding a room at a given moment, COMPUTED - as opposed to {@link RoomStatus},
 * which is what the room.status column says on disk, and {@link RoomAvailability}, which is
 * what one particular response calls it.
 *
 * A fourth vocabulary looks like the last thing this code needed, since three overlapping
 * ones were already a documented problem. It is the opposite: this is the one the others are
 * now DERIVED from - toStoredStatus() and toAvailability() below - so there is one
 * computation with three renderings instead of three computations that happened to agree.
 *
 * They did not entirely agree, which is why this exists. The precedence
 * MAINTENANCE > BLOCKED > BOOKED was written three times in three styles: two for-loops in
 * BookingService.getRoomStatus, two anyMatch in BookingService.updateRoomStatus, and a third
 * loop inside RoomService.toRoomDetails. The third differed from the other two in two ways
 * nobody could have called deliberate:
 *
 *  - it stopped at the FIRST booking overlapping the moment, whatever its status. A cancelled
 *    booking sitting in front of a real one therefore hid it, and the room reported itself
 *    FREE while it was booked. Reachable by cancelling a booking and re-booking the same
 *    slot, and which of the two came first was down to the database, since that query has no
 *    ORDER BY;
 *  - it treated the legacy CONFIRMED status as not occupying the room, while the other two
 *    treated it as occupying.
 *
 * Both are settled here in the direction the majority already took, and a room is now read
 * the same way whichever endpoint asks.
 *
 * The constants are declared from weakest to strongest so that compareTo IS the precedence:
 * a rank kept in a field would be a second thing to keep in step with the order.
 */
public enum RoomOccupancy {

    FREE,
    BOOKED,
    BLOCKED,
    MAINTENANCE;

    /**
     * The strongest claim on the room among the bookings holding it at that moment.
     *
     * The caller supplies the bookings and therefore owns the definition of "at that
     * moment": the repository has a query for it, RoomService filters in memory over the
     * bookings it already loaded. Anything that does not occupy the room - a cancelled
     * booking - contributes nothing, so passing a wider list than necessary is harmless.
     */
    public static RoomOccupancy of(Collection<Booking> holdingNow) {
        return strongestClaim(holdingNow).map(RoomOccupancy::from).orElse(FREE);
    }

    /**
     * The booking behind that claim, for callers that have to show it - which room details
     * does, since it names who booked or why the room is blocked.
     */
    public static Optional<Booking> strongestClaim(Collection<Booking> holdingNow) {
        Booking strongest = null;
        RoomOccupancy strongestClaim = FREE;

        for (Booking booking : holdingNow) {
            RoomOccupancy claim = from(booking);
            // Strictly greater: among equals the first wins, which keeps the answer stable
            // for the common case of one booking holding the room.
            if (claim.compareTo(strongestClaim) > 0) {
                strongest = booking;
                strongestClaim = claim;
            }
        }
        return Optional.ofNullable(strongest);
    }

    /** What the room.status column should say. BOOKED is called BUSY on disk. */
    public RoomStatus toStoredStatus() {
        return switch (this) {
            case FREE -> RoomStatus.FREE;
            case BOOKED -> RoomStatus.BUSY;
            case BLOCKED -> RoomStatus.BLOCKED;
            case MAINTENANCE -> RoomStatus.MAINTENANCE;
        };
    }

    /**
     * What the room-details response calls it. That vocabulary has no MAINTENANCE: to
     * whoever is looking at a room they cannot use, maintenance IS blocked, and the reason
     * travels in the block description rather than in the status.
     */
    public RoomAvailability toAvailability() {
        return switch (this) {
            case FREE -> RoomAvailability.FREE;
            case BOOKED -> RoomAvailability.BOOKED;
            case BLOCKED, MAINTENANCE -> RoomAvailability.BLOCKED;
        };
    }

    /** True for everything except FREE, meaning somebody or something has the room. */
    public boolean isOccupied() {
        return this != FREE;
    }

    private static RoomOccupancy from(Booking booking) {
        return from(booking.getStatus());
    }

    private static RoomOccupancy from(BookingStatus status) {
        if (status == null) {
            return FREE;
        }
        return switch (status) {
            case MAINTENANCE -> MAINTENANCE;
            case BLOCKED -> BLOCKED;
            // CONFIRMED is the legacy spelling of BOOKED and holds the room exactly as it
            // does: two of the three copies already agreed on that, the third did not.
            case BOOKED, CONFIRMED -> BOOKED;
            case CANCELLED -> FREE;
        };
    }
}
