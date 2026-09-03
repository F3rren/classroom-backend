package com.prenotazioni.prenotazione.model;

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
public class ProprietarioPrenotazione {

    @Column(name = "utente_id", nullable = false)
    private Long id;

    @Column(name = "utente_username", length = 50)
    private String username;

    @Column(name = "utente_nome", length = 100)
    private String nome;

    public ProprietarioPrenotazione() {
    }

    public ProprietarioPrenotazione(Long id, String username, String nome) {
        this.id = id;
        this.username = username;
        this.nome = nome;
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

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}
