package com.classroom.booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The data to create or update a room (administrators only)")
public record RoomRequest(
        @NotBlank(message = "Il nome dell'aula è obbligatorio.")
        @Schema(description = "The unique name of the room", example = "Aula Magna")
        String name,

        @Positive(message = "La capienza deve essere un numero positivo.")
        @Schema(description = "The maximum number of people, which must be positive", example = "120")
        int capacity,

        @PositiveOrZero(message = "Il piano deve essere un numero non negativo.")
        @Schema(description = "The floor of the building, 0 for the ground floor", example = "1")
        int floor,

        @JsonProperty("isVirtual")
        @Schema(description = "true for virtual rooms, which take up no physical space", example = "false")
        Boolean isVirtual) {
    // A single constructor, and only this one: Spring's @ModelAttribute binder
    // (BeanUtils.getResolvableConstructor) does not special-case records the way Jackson
    // does - it just requires the class to have exactly one declared constructor. A second,
    // convenience one (defaulting isVirtual) would make binding throw IllegalStateException
    // at request time instead of failing to compile.

    public RoomRequest {
        // isVirtual is genuinely optional (false when the caller omits it), but the binder
        // resolves an absent query parameter to null before this constructor ever runs - and
        // null cannot convert to a primitive boolean, which is why the component itself has
        // to be the wrapper type. This is where the default actually gets applied now.
        // isVirtual() therefore never actually returns null - existing call sites (RoomService,
        // tests) keep auto-unboxing it to a primitive boolean unchanged.
        if (isVirtual == null) {
            isVirtual = false;
        }
    }
}
