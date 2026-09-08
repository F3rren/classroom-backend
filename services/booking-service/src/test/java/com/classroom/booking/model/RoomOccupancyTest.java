package com.classroom.booking.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The precedence that used to be written three times, in three styles, in two classes.
 *
 * Two of those copies disagreed with the other in ways nobody could have called deliberate,
 * and both disagreements are pinned here so they cannot come back.
 */
class RoomOccupancyTest {

    private static Booking held(BookingStatus status) {
        Booking booking = new Booking();
        booking.setStatus(status);
        booking.setStartTime(LocalDateTime.now().minusHours(1));
        booking.setEndTime(LocalDateTime.now().plusHours(1));
        return booking;
    }

    @Test
    void nothingHoldingTheRoomMeansFree() {
        assertThat(RoomOccupancy.of(List.of())).isEqualTo(RoomOccupancy.FREE);
        assertThat(RoomOccupancy.strongestClaim(List.of())).isEmpty();
    }

    @Test
    void maintenanceBeatsEverything() {
        assertThat(RoomOccupancy.of(List.of(
                held(BookingStatus.BOOKED), held(BookingStatus.BLOCKED), held(BookingStatus.MAINTENANCE))))
                .isEqualTo(RoomOccupancy.MAINTENANCE);
    }

    @Test
    void blockedBeatsBooked() {
        assertThat(RoomOccupancy.of(List.of(held(BookingStatus.BOOKED), held(BookingStatus.BLOCKED))))
                .isEqualTo(RoomOccupancy.BLOCKED);
    }

    @Test
    void theOrderOfTheListDoesNotChangeTheAnswer() {
        // The precedence is over the whole set, not over whichever row the database returned
        // first. One of the three copies stopped at the first booking it met, which is
        // exactly what made the answer depend on a query with no ORDER BY.
        List<Booking> oneWay = List.of(held(BookingStatus.MAINTENANCE), held(BookingStatus.BOOKED));
        List<Booking> theOther = List.of(held(BookingStatus.BOOKED), held(BookingStatus.MAINTENANCE));

        assertThat(RoomOccupancy.of(oneWay)).isEqualTo(RoomOccupancy.of(theOther))
                .isEqualTo(RoomOccupancy.MAINTENANCE);
    }

    @Test
    void aCancelledBookingCannotHideARealOne() {
        // The defect this replaced: the room-details loop stopped at the first booking
        // overlapping the moment WHATEVER its status, so a cancelled one in front of a real
        // one left the room reading FREE. Reachable by cancelling a booking and re-booking
        // the same slot.
        List<Booking> cancelledFirst = List.of(held(BookingStatus.CANCELLED), held(BookingStatus.BOOKED));

        assertThat(RoomOccupancy.of(cancelledFirst)).isEqualTo(RoomOccupancy.BOOKED);
        assertThat(RoomOccupancy.strongestClaim(cancelledFirst))
                .hasValueSatisfying(booking ->
                        assertThat(booking.getStatus()).isEqualTo(BookingStatus.BOOKED));
    }

    @Test
    void aCancelledBookingOnItsOwnLeavesTheRoomFree() {
        assertThat(RoomOccupancy.of(List.of(held(BookingStatus.CANCELLED)))).isEqualTo(RoomOccupancy.FREE);
    }

    @Test
    void theLegacyConfirmedStatusHoldsTheRoomLikeBooked() {
        // Two of the three copies agreed on this, the third did not: room details treated
        // CONFIRMED as not occupying, so a legacy row left the room reading free.
        assertThat(RoomOccupancy.of(List.of(held(BookingStatus.CONFIRMED)))).isEqualTo(RoomOccupancy.BOOKED);
    }

    @Test
    void amongEqualClaimsTheFirstIsTheOneReported() {
        Booking first = held(BookingStatus.BOOKED);
        Booking second = held(BookingStatus.BOOKED);

        assertThat(RoomOccupancy.strongestClaim(List.of(first, second))).hasValue(first);
    }

    // ==================== the three renderings ====================

    @Test
    void theStoredColumnCallsABookedRoomBusy() {
        assertThat(RoomOccupancy.FREE.toStoredStatus()).isEqualTo(RoomStatus.FREE);
        assertThat(RoomOccupancy.BOOKED.toStoredStatus()).isEqualTo(RoomStatus.BUSY);
        assertThat(RoomOccupancy.BLOCKED.toStoredStatus()).isEqualTo(RoomStatus.BLOCKED);
        assertThat(RoomOccupancy.MAINTENANCE.toStoredStatus()).isEqualTo(RoomStatus.MAINTENANCE);
    }

    @Test
    void theDetailsResponseHasNoMaintenanceSoItReadsAsBlocked() {
        // To whoever is looking at a room they cannot use, maintenance IS blocked; the
        // reason travels in the block description rather than in the status.
        assertThat(RoomOccupancy.FREE.toAvailability()).isEqualTo(RoomAvailability.FREE);
        assertThat(RoomOccupancy.BOOKED.toAvailability()).isEqualTo(RoomAvailability.BOOKED);
        assertThat(RoomOccupancy.BLOCKED.toAvailability()).isEqualTo(RoomAvailability.BLOCKED);
        assertThat(RoomOccupancy.MAINTENANCE.toAvailability()).isEqualTo(RoomAvailability.BLOCKED);
    }

    @Test
    void theNamesAreTheContractOfTheRoomStatusEndpoint() {
        // getRoomStatus has always answered with these four uppercase words, and it now
        // answers with the constants' names. Renaming one would change the API, so it is
        // pinned here rather than left to whoever does the renaming to notice.
        assertThat(List.of(RoomOccupancy.values()).stream().map(Enum::name))
                .containsExactly("FREE", "BOOKED", "BLOCKED", "MAINTENANCE");
    }

    @Test
    void onlyFreeIsUnoccupied() {
        assertThat(RoomOccupancy.FREE.isOccupied()).isFalse();
        assertThat(RoomOccupancy.BOOKED.isOccupied()).isTrue();
        assertThat(RoomOccupancy.BLOCKED.isOccupied()).isTrue();
        assertThat(RoomOccupancy.MAINTENANCE.isOccupied()).isTrue();
    }
}
