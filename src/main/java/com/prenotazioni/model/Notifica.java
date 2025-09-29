package com.prenotazioni.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifiche")
public class Notifica {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utente_id", nullable = false)
    private Utente utente;
    
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

    // Constructors
    public Notifica() {
        this.dataCreazione = LocalDateTime.now();
    }
    
    public Notifica(Utente utente, String titolo, String messaggio, String tipo) {
        this();
        this.utente = utente;
        this.titolo = titolo;
        this.messaggio = messaggio;
        this.tipo = tipo;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Utente getUtente() { return utente; }
    public void setUtente(Utente utente) { this.utente = utente; }
    
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