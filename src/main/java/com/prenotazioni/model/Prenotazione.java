package com.prenotazioni.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entità Prenotazione - Basata su analisi frontend
 * Campi utilizzati dal frontend (normalizeBookingData):
 * - id, aulaId/roomId, corsoId/courseId, utenteId/userId
 * - inizio/startTime, fine/endTime, stato/status
 * - descrizione/description, dataCreazione/createdAt
 * - nomeAula/roomName, nomeCorso/courseName (calcolati via JOIN)
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "prenotazione")
public class Prenotazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aula_id", nullable = false)
    private Aula aula;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "corso_id", nullable = true) // Nullable per blocchi admin
    private Corso corso;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "utente_id", nullable = false)
    private Utente utente;
    
    @Column(nullable = false)
    private LocalDateTime inizio;
    
    @Column(nullable = false)
    private LocalDateTime fine;
    
    @Column(nullable = false, length = 20)
    private String stato; // 'prenotata', 'confermata', 'bloccata', 'manutenzione', 'annullata'
    
    @Column(columnDefinition = "TEXT")
    private String descrizione;
    
    @Column(name = "data_creazione", nullable = false, updatable = false)
    private LocalDateTime dataCreazione;

    @PrePersist
    protected void onCreate() {
        if (dataCreazione == null) {
            dataCreazione = LocalDateTime.now();
        }
        if (stato == null || stato.isEmpty()) {
            stato = "prenotata";
        }
    }
}
