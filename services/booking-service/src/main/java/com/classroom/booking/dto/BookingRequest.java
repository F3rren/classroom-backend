package com.classroom.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

/**
 * A request to create, update or block a booking.
 *
 * start and end stay Strings (in the form "2024-12-25T14:30:00"): the parsing and the range
 * checks (end after start, not in the past, and so on) are application logic done by hand in
 * the controller, after this presence validation has passed.
 */
@Schema(description = "The data of a booking, used to create it, update it, or block a room")
public record BookingRequest(
        @NotNull(message = "Devi specificare quale aula vuoi prenotare.")
        @Schema(description = "The id of the room to book", example = "3")
        Long roomId,

        @Schema(description = "The id of the associated course. Optional: absent for a booking with no course", example = "12")
        Long courseId,

        @NotBlank(message = "Devi specificare quando inizia la prenotazione.")
        @Schema(description = "The start of the booking, ISO format without a time zone. Must be in the future",
                example = "2026-12-25T14:30:00")
        String startTime,

        @NotBlank(message = "Devi specificare quando finisce la prenotazione.")
        @Schema(description = "The end of the booking, which must come after the start",
                example = "2026-12-25T16:30:00")
        String endTime,

        @Schema(description = "Free-text description, shown in the room details", example = "Lezione di Analisi 1")
        String description) {

    // Explicit, not the generated one: it is the only way to keep @NonNull on the accessor
    // past @Valid's @NotNull check, same as the Lombok @Getter(onMethod_) this replaces. The
    // return type still has to be exactly Long - a record accessor cannot change it.
    public @NonNull Long roomId() {
        return roomId;
    }
}
