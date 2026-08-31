package com.prenotazioni.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Entità Utente - Basata su analisi frontend
 * Campi utilizzati dal frontend:
 * - id, username, nome, email, ruolo, dataRegistrazione, ultimoAccesso
 *
 * IMPORTANTE: ruolo DEVE essere in minuscolo ('admin' o 'user')
 * Frontend controlla: user?.ruolo === "admin"
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "utenti")
// Quando Utente e' referenziato come relazione LAZY (es. Notifica.utente), Hibernate lo
// carica come subclasse proxy che aggiunge un getter pubblico "hibernateLazyInitializer";
// senza questa esclusione Jackson prova a serializzarlo e fallisce con
// InvalidDefinitionException (nessun serializzatore per ByteBuddyInterceptor).
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Utente {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    
    @Column(nullable = false, length = 100)
    private String nome;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    @Column(nullable = false, length = 255)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;
    
    @Column(nullable = false, length = 20)
    private String ruolo; // 'admin' o 'user' (MINUSCOLO!)
    
    @Column(name = "data_registrazione", nullable = false, updatable = false)
    private LocalDateTime dataRegistrazione;
    
    @Column(name = "ultimo_accesso")
    private LocalDateTime ultimoAccesso;
    
    @PrePersist
    protected void onCreate() {
        if (dataRegistrazione == null) {
            dataRegistrazione = LocalDateTime.now();
        }
        // Normalizza il ruolo in minuscolo
        if (ruolo != null) {
            ruolo = ruolo.toLowerCase();
        } else {
            ruolo = "user"; // Default
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        // Normalizza il ruolo in minuscolo ad ogni aggiornamento
        if (ruolo != null) {
            ruolo = ruolo.toLowerCase();
        }
    }
}
