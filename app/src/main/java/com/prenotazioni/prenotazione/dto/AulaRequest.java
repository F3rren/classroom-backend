package com.prenotazioni.prenotazione.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "Dati per creare o modificare un'aula (solo amministratori)")
@NoArgsConstructor
public class AulaRequest {
    @NotBlank(message = "Il nome dell'aula è obbligatorio.")
    @Schema(description = "Nome univoco dell'aula", example = "Aula Magna")
    private String nome;

    @Positive(message = "La capienza deve essere un numero positivo.")
    @Schema(description = "Numero massimo di persone, deve essere positivo", example = "120")
    private int capienza;

    @PositiveOrZero(message = "Il piano deve essere un numero non negativo.")
    @Schema(description = "Piano dell'edificio, 0 per il piano terra", example = "1")
    private int piano;

    @JsonProperty("isVirtual")
    @Schema(description = "true per le aule virtuali, che non occupano spazio fisico", example = "false")
    private boolean isVirtual = false;

    public AulaRequest(String nome, int capienza, int piano) {
        this.nome = nome;
        this.capienza = capienza;
        this.piano = piano;
        this.isVirtual = false;
    }

    public AulaRequest(String nome, int capienza, int piano, boolean isVirtual) {
        this.nome = nome;
        this.capienza = capienza;
        this.piano = piano;
        this.isVirtual = isVirtual;
    }
}
