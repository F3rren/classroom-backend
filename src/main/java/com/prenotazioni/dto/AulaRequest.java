package com.prenotazioni.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class AulaRequest {
    @NotBlank(message = "Il nome dell'aula è obbligatorio.")
    private String nome;

    @Positive(message = "La capienza deve essere un numero positivo.")
    private int capienza;

    @PositiveOrZero(message = "Il piano deve essere un numero non negativo.")
    private int piano;

    @JsonProperty("isVirtual")
    private boolean isVirtual = false;

    // Costruttori
    public AulaRequest() {}

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

    // Getter e Setter
    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public int getCapienza() {
        return capienza;
    }

    public void setCapienza(int capienza) {
        this.capienza = capienza;
    }

    public int getPiano() {
        return piano;
    }

    public void setPiano(int piano) {
        this.piano = piano;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public void setVirtual(boolean isVirtual) {
        this.isVirtual = isVirtual;
    }
}
