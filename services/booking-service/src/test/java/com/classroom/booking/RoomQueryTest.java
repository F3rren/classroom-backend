package com.classroom.booking;

import com.classroom.testsupport.TestJson;
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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the read endpoints of /api/rooms that had no test until now: /capacity, /detailed,
 * /details, /stats, /virtual, /virtual/detailed, /physical/detailed e /{id}/detailed.
 *
 * The dataset deliberately has 2 physical rooms and 1 virtual one with different capacities,
 * so the filters and the statistics produce distinguishable numbers instead of all zeroes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomQueryTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private String token;
    private Long largePhysicalRoomId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();


        largePhysicalRoomId = saveRoom("Aula Grande", 1, 100, false);
        saveRoom("Aula Piccola", 2, 10, false);
        saveRoom("Aula Virtuale", 0, 50, true);

        token = TestJwt.forUser(1L, "roomquery@test.it", "Room Query");
    }

    private Long saveRoom(String name, int floor, int capacity, boolean virtual) {
        Room a = new Room();
        a.setName(name);
        a.setFloor(floor);
        a.setCapacity(capacity);
        a.setVirtual(virtual);
        a.setStatus(RoomStatus.FREE);
        return roomRepository.save(a).getId();
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<String> get(String url) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(Objects.requireNonNull(token));
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<String> resp) throws Exception {
        return (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
    }

    @Test
    void theCapacityFilterKeepsOnlyRoomsAboveTheThreshold() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capacity?minCapacity=50");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        // the large room (100) and the virtual one (50) pass, the small one (10) does not
        assertThat(data.get("totalRooms")).isEqualTo(2);
        assertThat(data).containsEntry("minCapacity", 50);
        assertThat(data).containsEntry("maxCapacityFound", 100);
    }

    @Test
    void aCapacityWithNoMatchesReturnsASuggestion() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capacity?minCapacity=999");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.get("totalRooms")).isEqualTo(0);
        assertThat(data).containsKey("suggestion");
        assertThat(data).doesNotContainKey("maxCapacityFound");
    }

    @Test
    void aCapacityBeyondTheMaximumIsRejected() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capacity?minCapacity=1001");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("CAPACITY_TOO_HIGH");
    }

    @Test
    void aNegativeCapacityIsRejected() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capacity?minCapacity=-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("INVALID_CAPACITY");
    }

    @Test
    void virtualRoomsAreTaggedAndSeparatedFromPhysical() throws Exception {
        Map<String, Object> virtualRooms = dataOf(get("/api/rooms/virtual"));
        assertThat(virtualRooms).containsEntry("type", "virtual");
        assertThat(virtualRooms.get("totalRooms")).isEqualTo(1);

        Map<String, Object> physicalRooms = dataOf(get("/api/rooms/physical"));
        assertThat(physicalRooms).containsEntry("type", "physical");
        assertThat(physicalRooms.get("totalRooms")).isEqualTo(2);
    }

    @Test
    void theDetailedVariantsReturnTheSameCounts() throws Exception {
        assertThat(dataOf(get("/api/rooms/detailed")).get("totalRooms")).isEqualTo(3);
        assertThat(dataOf(get("/api/rooms/physical/detailed")).get("totalRooms")).isEqualTo(2);
        assertThat(dataOf(get("/api/rooms/virtual/detailed")).get("totalRooms")).isEqualTo(1);
    }

    @Test
    void thePhysicalDetailIsLabelledAsPhysical() throws Exception {
        assertThat(dataOf(get("/api/rooms/physical/detailed"))).containsEntry("type", "physical");
        assertThat(dataOf(get("/api/rooms/virtual/detailed"))).containsEntry("type", "virtual");
    }

    @Test
    void theStatisticsSeparatePhysicalAndVirtualRooms() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/stats");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) dataOf(resp).get("statistics");
        assertThat(stats.keySet()).containsExactlyInAnyOrder(
                "totalRooms", "physicalRooms", "virtualRooms",
                "physicalPercentage", "virtualPercentage", "hasRooms");
        assertThat(stats.get("totalRooms")).isEqualTo(3);
        assertThat(stats.get("physicalRooms")).isEqualTo(2);
        assertThat(stats.get("virtualRooms")).isEqualTo(1);
        assertThat(stats.get("hasRooms")).isEqualTo(true);
    }

    @Test
    void theDetailByIdIsWrappedInTheRoomKey() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + largePhysicalRoomId + "/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.keySet()).containsExactly("room");
    }

    @Test
    void theDetailOfAMissingRoomAnswers404() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/999999/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void theDetailOfEveryRoomIsNotWrappedInTheEnvelope() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        // shape storica: nessun envelope success/data
        assertThat(body.keySet()).containsExactlyInAnyOrder("bookings", "totalBookings");
    }

    @Test
    void theRoomQueryEndpointsRequireAuthentication() {
        for (String url : new String[]{
                "/api/rooms/stats", "/api/rooms/virtual", "/api/rooms/detailed",
                "/api/rooms/capacity?minCapacity=1"}) {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            assertThat(resp.getStatusCode())
                    .as("endpoint %s with no token", url)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
