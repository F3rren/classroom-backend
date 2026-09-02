package com.prenotazioni.notifica.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Una notifica destinata a un utente.
 *
 * L'utente e' un semplice identificativo e non piu' una @ManyToOne: questo servizio non
 * possiede la tabella utenti e non puo' avere una chiave esterna verso un altro database.
 * La conseguenza da conoscere e' che nulla impedisce piu' a livello di database una
 * notifica per un utente inesistente; e' responsabilita' applicativa.
 *
 * I campi nomeStanza, adminNome, prenotazioneId e dataPrenotazione erano gia' denormalizzati
 * prima della separazione: la notifica nasce autosufficiente, ed e' il motivo per cui questo
 * dominio si stacca senza dover chiamare nessuno per rendere le proprie risposte.
 */
@Entity
@Table(name = "notifiche")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Notifica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utente_id", nullable = false)
    private Long utenteId;

    @Column(name = "titolo", nullable = false, length = 200)
    private String titolo;

    @Column(name = "messaggio", nullable = false, length = 1000)
    private String messaggio;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo; // INFO, WARNING, ERROR, SUCCESS

    @Column(name = "letta", nullable = false)
    private Boolean letta = false;

    @Column(name = "data_creazione", nullable = false)
    private LocalDateTime dataCreazione;

    @Column(name = "data_lettura")
    private LocalDateTime dataLettura;

    // Dati aggiuntivi per le notifiche di prenotazione
    @Column(name = "prenotazione_id")
    private Long prenotazioneId;

    @Column(name = "nome_stanza", length = 100)
    private String nomeStanza;

    @Column(name = "data_prenotazione")
    private LocalDateTime dataPrenotazione;

    @Column(name = "admin_nome", length = 100)
    private String adminNome;

    public Notifica() {
        this.dataCreazione = LocalDateTime.now();
    }

    public Notifica(Long utenteId, String titolo, String messaggio, String tipo) {
        this();
        this.utenteId = utenteId;
        this.titolo = titolo;
        this.messaggio = messaggio;
        this.tipo = tipo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUtenteId() { return utenteId; }
    public void setUtenteId(Long utenteId) { this.utenteId = utenteId; }

    public String getTitolo() { return titolo; }
    public void setTitolo(String titolo) { this.titolo = titolo; }

    public String getMessaggio() { return messaggio; }
    public void setMessaggio(String messaggio) { this.messaggio = messaggio; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Boolean getLetta() { return letta; }
    public void setLetta(Boolean letta) { this.letta = letta; }

    public LocalDateTime getDataCreazione() { return dataCreazione; }
    public void setDataCreazione(LocalDateTime dataCreazione) { this.dataCreazione = dataCreazione; }

    public LocalDateTime getDataLettura() { return dataLettura; }
    public void setDataLettura(LocalDateTime dataLettura) { this.dataLettura = dataLettura; }

    public Long getPrenotazioneId() { return prenotazioneId; }
    public void setPrenotazioneId(Long prenotazioneId) { this.prenotazioneId = prenotazioneId; }

    public String getNomeStanza() { return nomeStanza; }
    public void setNomeStanza(String nomeStanza) { this.nomeStanza = nomeStanza; }

    public LocalDateTime getDataPrenotazione() { return dataPrenotazione; }
    public void setDataPrenotazione(LocalDateTime dataPrenotazione) { this.dataPrenotazione = dataPrenotazione; }

    public String getAdminNome() { return adminNome; }
    public void setAdminNome(String adminNome) { this.adminNome = adminNome; }
}
