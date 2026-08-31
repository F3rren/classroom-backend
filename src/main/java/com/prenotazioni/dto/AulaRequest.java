package com.prenotazioni.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AulaRequest {
    @NotBlank(message = "Il nome dell'aula è obbligatorio.")
    private String nome;

    @Positive(message = "La capienza deve essere un numero positivo.")
    private int capienza;

    @PositiveOrZero(message = "Il piano deve essere un numero non negativo.")
    private int piano;

    @JsonProperty("isVirtual")
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
