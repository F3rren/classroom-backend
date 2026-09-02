package com.prenotazioni.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entità Aula - Basata su analisi frontend
 * Campi utilizzati dal frontend (normalizeRoomData):
 * - id, nome/name, capienza/capacity, piano/floor, isVirtual, descrizione/description, stato/status
 * 
 * Il frontend normalizza i dati quindi possiamo usare nomi italiani
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "aule")
public class Aula {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String nome;
    
    @Column(nullable = false)
    private int capienza;
    
    @Column(nullable = false)
    private int piano;
    
    @Column(name = "is_virtual", nullable = false)
    @JsonProperty("isVirtual")
    private boolean isVirtual = false;
    
    @Column(columnDefinition = "TEXT")
    private String descrizione;
    
    @Column(length = 20)
    // Persistito minuscolo dal converter di StatoAula (CHECK constraint aula_stato_check)
    private StatoAula stato;
    
    @PrePersist
    @PreUpdate
    protected void setDefaults() {
        if (stato == null) {
            stato = StatoAula.LIBERA;
        }
    }
}
