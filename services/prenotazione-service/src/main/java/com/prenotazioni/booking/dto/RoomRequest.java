package com.prenotazioni.booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "The data to create or update a room (administrators only)")
@NoArgsConstructor
public class RoomRequest {
    @NotBlank(message = "Il nome dell'aula è obbligatorio.")
    @Schema(description = "The unique name of the room", example = "Aula Magna")
    private String name;

    @Positive(message = "La capienza deve essere un numero positivo.")
    @Schema(description = "The maximum number of people, which must be positive", example = "120")
    private int capacity;

    @PositiveOrZero(message = "Il piano deve essere un numero non negativo.")
    @Schema(description = "The floor of the building, 0 for the ground floor", example = "1")
    private int floor;

    @JsonProperty("isVirtual")
    @Schema(description = "true for virtual rooms, which take up no physical space", example = "false")
    private boolean isVirtual = false;

    public RoomRequest(String name, int capacity, int floor) {
        this.name = name;
        this.capacity = capacity;
        this.floor = floor;
        this.isVirtual = false;
    }

    public RoomRequest(String name, int capacity, int floor, boolean isVirtual) {
        this.name = name;
        this.capacity = capacity;
        this.floor = floor;
        this.isVirtual = isVirtual;
    }
}
