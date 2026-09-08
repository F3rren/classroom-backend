package com.classroom.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import org.springframework.lang.NonNull;

/**
 * A request to create, update or block a booking.
 *
 * start and end stay Strings (in the form "2024-12-25T14:30:00"): the parsing and the range
 * checks (end after start, not in the past, and so on) are application logic done by hand in
 * the controller, after this presence validation has passed.
 */
@Data
@Schema(description = "The data of a booking, used to create it, update it, or block a room")
public class BookingRequest {

    @NotNull(message = "Devi specificare quale aula vuoi prenotare.")
    @Schema(description = "The id of the room to book", example = "3")
    @Getter(onMethod_ = {@NonNull})
    private Long roomId;

    @Schema(description = "The id of the associated course. Optional: absent for a booking with no course", example = "12")
    private Long courseId;

    @NotBlank(message = "Devi specificare quando inizia la prenotazione.")
    @Schema(description = "The start of the booking, ISO format without a time zone. Must be in the future",
            example = "2026-12-25T14:30:00")
    private String startTime;

    @NotBlank(message = "Devi specificare quando finisce la prenotazione.")
    @Schema(description = "The end of the booking, which must come after the start",
            example = "2026-12-25T16:30:00")
    private String endTime;

    @Schema(description = "Free-text description, shown in the room details", example = "Lezione di Analisi 1")
    private String description;
}
