package com.prenotazioni.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Chi ha effettuato una prenotazione, come istantanea presa al momento della prenotazione.
 *
 * Prima era una @ManyToOne verso l'entita' Utente. Quella relazione attraversa il confine
 * fra due servizi: gli utenti appartengono ad auth-service, le prenotazioni no. Restando
 * una join, ogni lettura di prenotazione avrebbe richiesto una chiamata di rete.
 *
 * I tre campi sono esattamente quelli che sanitizeOwnerForListing gia' esponeva nel JSON
 * (id, username, nome), quindi la forma della risposta non cambia: e' un @Embeddable e non
 * un'entita' proprio per continuare a serializzarsi come oggetto "utente" annidato.
 * Email, ruolo e date di accesso non sono mai stati esposti e continuano a non esserlo.
 *
 * E' un'istantanea per scelta: mostra chi ha prenotato COME ERA ALLORA. Se l'utente cambia
 * nome, lo storico non si riscrive. Per la stessa ragione questi campi non vanno
 * risincronizzati quando auth-service aggiorna un profilo.
 */
@Embeddable
public class BookingOwner {

    @Column(name = "user_id", nullable = false)
    private Long id;

    @Column(name = "user_username", length = 50)
    private String username;

    @Column(name = "user_name", length = 100)
    private String name;

    public BookingOwner() {
    }

    public BookingOwner(Long id, String username, String name) {
        this.id = id;
        this.username = username;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
