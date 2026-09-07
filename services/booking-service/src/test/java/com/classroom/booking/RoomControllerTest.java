package com.classroom.booking;

import com.classroom.testsupport.TestJson;
import com.classroom.booking.model.BookingOwner;
import com.classroom.testsupport.TestJwt;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.RoomStatus;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The regression suite for RoomController after it lost the manual
 * @RequestHeader("Authorization") / checkAuth, with typed responses (RoomListPayload and so on).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private String token;
    private Long roomId;

    @BeforeEach
    void setUp() {
        // Bookings BEFORE rooms: they hold a foreign key to them, and deleting a room that
        // is still referenced violates the constraint.
        //
        // It was missing, and this was the only one of the room classes not doing it. It
        // did not show because @DirtiesContext rebuilt the context - and with it the
        // in-memory H2 - after every class: the previous class's rows never existed. Once
        // that was removed the defect surfaced immediately, and it had always been there.
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        BookingOwner user = new BookingOwner(1L, "room-user", "Room User");

        Room room = new Room();
        room.setName("Aula Room Test");
        room.setFloor(3);
        room.setCapacity(15);
        room.setVirtual(false);
        room.setStatus(RoomStatus.FREE);
        roomId = roomRepository.save(room).getId();

        token = TestJwt.forUser(1L, "room-user@test.it", "Room User");
    }


    private HttpHeaders bearer() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }


    @Test
    void theRoomListReturnsATypedList() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.keySet()).containsExactlyInAnyOrder("rooms", "totalRooms");
        assertThat((Integer) data.get("totalRooms")).isEqualTo(1);
    }

    @Test
    void theRoomByIdCarriesTheDenormalisedFields() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/" + roomId, HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
        assertThat(data.keySet()).containsExactlyInAnyOrder("room", "roomId", "roomName", "floor", "capacity");
        assertThat(data.get("roomName")).isEqualTo("Aula Room Test");
        assertThat(data.get("floor")).isEqualTo(3);
    }

    @Test
    void aMissingRoomAnswers404() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/999999", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theRoomDetailIsNotWrappedInTheEnvelope() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/" + roomId + "/details", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        // No success/message/data envelope here: an existing shape, kept as it was
        assertThat(body.keySet()).containsExactlyInAnyOrder("room", "bookings", "totalBookings");
    }

    @Test
    void roomsByFloorIncludeTheFloorField() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/floor/3", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
        assertThat(data).containsEntry("floor", 3);
        assertThat(data).doesNotContainKey("type"); // an optional field, omitted when unused
    }

    @Test
    void physicalRoomsIncludeTheTypeField() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/physical", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
        assertThat(data).containsEntry("type", "physical");
        assertThat(data).doesNotContainKey("floor");
    }

    @Test
    void theRoomEndpointsAnswer401WithoutAToken() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
