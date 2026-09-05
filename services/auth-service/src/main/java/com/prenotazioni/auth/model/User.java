package com.prenotazioni.auth.model;

import com.prenotazioni.model.Role;
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
@Table(name = "users")
// Quando Utente e' referenziato come relazione LAZY (es. Notifica.utente), Hibernate lo
// carica come subclasse proxy che aggiunge un getter pubblico "hibernateLazyInitializer";
// senza questa esclusione Jackson prova a serializzarlo e fallisce con
// InvalidDefinitionException (nessun serializzatore per ByteBuddyInterceptor).
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    @Column(nullable = false, length = 255)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;
    
    @Column(nullable = false, length = 20)
    // Persistito minuscolo dal converter di Ruolo (CHECK constraint utente_ruolo_check)
    private Role role;
    
    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    @PrePersist
    protected void onCreate() {
        if (registeredAt == null) {
            registeredAt = LocalDateTime.now();
        }
        // Nessuna normalizzazione del case: la conversione da stringa passa da
        // Ruolo.da(), che accetta qualunque case e restituisce sempre la costante giusta.
        if (role == null) {
            role = Role.USER;
        }
    }
    
}
