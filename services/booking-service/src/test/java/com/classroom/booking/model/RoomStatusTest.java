package com.classroom.booking.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The external contract of RoomStatus.
 *
 * These two tests used to live inside RoleTest, which covered two different enums. The
 * modularisation separated the cases: Role is common to every service and lives in shared,
 * RoomStatus belongs to the room domain and stays here.
 */
class RoomStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roomStatusKeepsItsOwnLowercaseVocabulary() {
        // room_status_check: 4 values, different from the booking's
        assertThat(java.util.Arrays.stream(RoomStatus.values()).map(s -> s.getValue()))
                .containsExactlyInAnyOrder("free", "busy", "blocked", "maintenance");
    }

    @Test
    void roomStatusSerialisesLowercase() throws Exception {
        assertThat(objectMapper.writeValueAsString(RoomStatus.BUSY)).isEqualTo("\"busy\"");
        assertThat(objectMapper.writeValueAsString(RoomStatus.FREE)).isEqualTo("\"free\"");
    }
}
