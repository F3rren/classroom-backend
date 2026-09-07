package com.classroom.booking;

import com.classroom.testsupport.TestJson;
import com.classroom.testsupport.TestJwt;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.RoomStatus;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.model.BookingOwner;
import com.classroom.booking.repository.RoomRepository;
import com.classroom.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoomService holds the same "room details" block cloned three times
 * (getAllRoomsWithDetails, getRoomWithDetails and the private getRoomsDetailsFromList).
 * All three were already invoked by RoomQueryTest, but its fixture creates no bookings at
 * all: so every per-booking branch (busy now, blocked, under maintenance, imminent, the
 * booking list) was never executed.
 *
 * This class supplies the missing fixture. The bookings are inserted through the repository
 * and not over HTTP, because the controller refuses dates in the past and what is needed
 * here are bookings that straddle RIGHT NOW.
 *
 * Each room covers exactly one scenario: the blocks break on the first overlapping booking,
 * so putting several cases on the same room would hide some of them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomDetailsWithBookingsTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private String token;
    private Long busyRoomId;
    private Long blockedRoomId;
    private Long maintenanceRoomId;
    private Long upcomingRoomId;
    private Long freeRoomId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        // The name ends up inside currentBooking, so it matters that it is filled in.
        BookingOwner user = new BookingOwner(1L, "dettagli", "Mario Rossi");

        LocalDateTime now = LocalDateTime.now();

        busyRoomId = saveRoom("Aula Occupata", 1, 30, false);
        book(busyRoomId, user, BookingStatus.BOOKED, now.minusHours(1), now.plusHours(1), "Lezione di Analisi");

        blockedRoomId = saveRoom("Aula Bloccata", 1, 30, false);
        book(blockedRoomId, user, BookingStatus.BLOCKED, now.minusHours(1), now.plusHours(1), "Evento riservato");

        maintenanceRoomId = saveRoom("Aula Manutenzione", 2, 20, false);
        book(maintenanceRoomId, user, BookingStatus.MAINTENANCE, now.minusHours(1), now.plusHours(1), "Sostituzione proiettore");

        // A virtual room with an IMMINENT booking (within 2 hours, but not yet started):
        // covers the second branch and the virtual side of the third clone.
        upcomingRoomId = saveRoom("Aula Virtuale Imminente", 0, 50, true);
        book(upcomingRoomId, user, BookingStatus.BOOKED, now.plusMinutes(30), now.plusMinutes(90), null);

        freeRoomId = saveRoom("Aula Libera", 3, 10, false);

        token = TestJwt.forUser(1L, "roomdetailswithbookingstest@test.it", "Utente Test");
    }

    private Long saveRoom(String name, int floor, int capacity, boolean virtuale) {
        Room a = new Room();
        a.setName(name);
        a.setFloor(floor);
        a.setCapacity(capacity);
        a.setVirtual(virtuale);
        a.setStatus(RoomStatus.FREE);
        return roomRepository.save(a).getId();
    }

    private void book(Long roomId, BookingOwner user, BookingStatus status,
                         LocalDateTime startTime, LocalDateTime endTime, String description) {
        Booking p = new Booking();
        p.setRoom(roomRepository.findById(roomId).orElseThrow());
        p.setUser(user);
        p.setStartTime(startTime);
        p.setEndTime(endTime);
        p.setStatus(status);
        p.setDescription(description);
        p.setCreatedAt(LocalDateTime.now()); // letto da blockInfo.blockedAt
        bookingRepository.save(p);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<String> get(String url) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }


    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> roomsOf(ResponseEntity<String> resp) throws Exception {
        Map<String, Object> data = (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
        return (List<Map<String, Object>>) data.get("rooms");
    }

    private Map<String, Object> byName(List<Map<String, Object>> rooms, String name) {
        return rooms.stream()
                .filter(r -> name.equals(r.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aula non trovata nella risposta: " + name));
    }

    // ==================== clone 1: /api/rooms/detailed ====================

    @Test
    void theDetailReportsTheRoomBusyRightNow() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> occupata = byName(rooms, "Aula Occupata");

        assertThat(occupata.get("status")).isEqualTo("booked");

        @SuppressWarnings("unchecked")
        Map<String, Object> booking = (Map<String, Object>) occupata.get("booking");
        assertThat(booking).isNotNull();
        assertThat(booking.get("user")).isEqualTo("Mario Rossi");
        assertThat(booking.get("purpose")).isEqualTo("Lezione di Analisi");
        assertThat((String) booking.get("time")).contains("-");
    }

    @Test
    void theDetailReportsABlockedRoomWithTheBlockData() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> bloccata = byName(rooms, "Aula Bloccata");

        assertThat(bloccata.get("status")).isEqualTo("blocked");

        @SuppressWarnings("unchecked")
        Map<String, Object> blocked = (Map<String, Object>) bloccata.get("blocked");
        assertThat(blocked).isNotNull();
        assertThat(blocked.get("reason")).isEqualTo("Evento riservato");
        assertThat(blocked.get("blockedBy")).isEqualTo("admin");
    }

    @Test
    void theDetailTreatsMaintenanceAsABlock() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> manutenzione = byName(rooms, "Aula Manutenzione");

        assertThat(manutenzione.get("status")).isEqualTo("blocked");
        assertThat(manutenzione.get("blocked")).isNotNull();
    }

    @Test
    void theDetailFlagsABookingStartingWithinTwoHours() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> imminente = byName(rooms, "Aula Virtuale Imminente");

        // not busy right now, but starting within 2 hours -> "booked" all the same
        assertThat(imminente.get("status")).isEqualTo("booked");

        @SuppressWarnings("unchecked")
        Map<String, Object> booking = (Map<String, Object>) imminente.get("booking");
        assertThat(booking).isNotNull();
        // a null description -> the "Lezione" fallback
        assertThat(booking.get("purpose")).isEqualTo("Lezione");
    }

    @Test
    void theDetailLeavesARoomWithNoBookingsFree() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> libera = byName(rooms, "Aula Libera");

        assertThat(libera.get("status")).isEqualTo("free");
        assertThat(libera.get("booking")).isNull();
        assertThat(libera.get("blocked")).isNull();
    }

    @Test
    void theDetailIncludesTheListOfBookings() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bookings =
                (List<Map<String, Object>>) byName(rooms, "Aula Occupata").get("bookings");

        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).keySet())
                .containsExactlyInAnyOrder("date", "startTime", "endTime", "user", "purpose");
        assertThat(bookings.get(0).get("user")).isEqualTo("Mario Rossi");
    }

    // ==================== clone 2: /api/rooms/{id}/detailed ====================

    @Test
    void theSingleRoomDetailReportsTheBookingInProgress() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + busyRoomId + "/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("booked");
        assertThat(room.get("booking")).isNotNull();
    }

    @Test
    void theSingleRoomDetailReportsTheBlockData() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + blockedRoomId + "/detailed");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("blocked");
        assertThat(room.get("blocked")).isNotNull();
    }

    @Test
    void theSingleRoomDetailFlagsTheImminentBooking() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + upcomingRoomId + "/detailed");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("booked");
    }

    // ==================== clone 3: physical/virtual detailed ====================

    @Test
    void thePhysicalDetailCarriesTheStatusOnlyForPhysicalRooms() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/physical/detailed"));

        assertThat(rooms).hasSize(4); // le 4 aule fisiche, l'aula virtuale e' esclusa
        assertThat(byName(rooms, "Aula Occupata").get("status")).isEqualTo("booked");
        assertThat(byName(rooms, "Aula Bloccata").get("status")).isEqualTo("blocked");
        assertThat(byName(rooms, "Aula Libera").get("status")).isEqualTo("free");
    }

    @Test
    void theVirtualDetailCarriesTheStatusOnlyForVirtualRooms() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/virtual/detailed"));

        assertThat(rooms).hasSize(1);
        assertThat(byName(rooms, "Aula Virtuale Imminente").get("status")).isEqualTo("booked");
    }

    // ==================== room status (BookingService.getRoomStatus) ====================

    @Test
    void roomStatusReflectsTheActiveBookingKind() throws Exception {
        assertThat(statusOf(busyRoomId)).isEqualTo("BOOKED");
        assertThat(statusOf(blockedRoomId)).isEqualTo("BLOCKED");
        assertThat(statusOf(maintenanceRoomId)).isEqualTo("MAINTENANCE");
        assertThat(statusOf(freeRoomId)).isEqualTo("FREE");
        // the imminent booking is not active yet: the room comes out free right now
        assertThat(statusOf(upcomingRoomId)).isEqualTo("FREE");
    }

    private String statusOf(Long roomId) throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/room-status/" + roomId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) TestJson.asMap(resp.getBody()).get("status");
    }

}
