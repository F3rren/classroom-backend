package com.prenotazioni.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entità Corso - Basata su analisi frontend
 * Campi utilizzati: id, nome, docente, descrizione
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "corsi")
public class Corso {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String nome;
    
    @Column(nullable = false, length = 100)
    private String docente;
    
    @Column(columnDefinition = "TEXT")
    private String descrizione;
}
