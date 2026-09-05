package com.prenotazioni.notification.model;

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
@Table(name = "notifications")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "type", nullable = false, length = 50)
    private String type; // INFO, WARNING, ERROR, SUCCESS

    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // Dati aggiuntivi per le notifiche di prenotazione
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Column(name = "booking_date")
    private LocalDateTime bookingDate;

    @Column(name = "admin_name", length = 100)
    private String adminName;

    public Notification() {
        this.createdAt = LocalDateTime.now();
    }

    public Notification(Long userId, String title, String message, String type) {
        this();
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public LocalDateTime getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDateTime bookingDate) { this.bookingDate = bookingDate; }

    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }
}
