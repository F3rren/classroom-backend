package com.prenotazioni.prenotazione.model;

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
@Table(name = "prenotazioni")
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
    
    // Istantanea e non relazione: gli utenti vivono in un altro servizio.
    // Vedi ProprietarioPrenotazione per il perche' e per le conseguenze.
    @Embedded
    private ProprietarioPrenotazione utente;
    
    @Column(nullable = false)
    private LocalDateTime inizio;
    
    @Column(nullable = false)
    private LocalDateTime fine;
    
    // Persistito come stringa minuscola dal converter di StatoPrenotazione, per restare
    // compatibile con il CHECK constraint prenotazione_stato_check e col frontend.
    @Column(nullable = false, length = 20)
    private StatoPrenotazione stato;
    
    @Column(columnDefinition = "TEXT")
    private String descrizione;
    
    @Column(name = "data_creazione", nullable = false, updatable = false)
    private LocalDateTime dataCreazione;

    @PrePersist
    protected void onCreate() {
        if (dataCreazione == null) {
            dataCreazione = LocalDateTime.now();
        }
        if (stato == null) {
            stato = StatoPrenotazione.PRENOTATA;
        }
    }
}
