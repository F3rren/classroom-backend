package com.prenotazioni.notification.service;

import com.prenotazioni.notification.model.Notification;
import com.prenotazioni.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getNotificationsByUser(Long userId) {
        logger.debug("INIZIO - Recupero notifiche per utente ID: {}", userId);
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        logger.debug("FINE - Recuperate {} notifiche per utente ID: {}", notifications.size(), userId);
        return notifications;
    }

    public List<Notification> getUnreadNotificationsByUser(Long userId) {
        logger.debug("INIZIO - Recupero notifiche non lette per utente ID: {}", userId);
        List<Notification> notifications = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        logger.debug("FINE - Recuperate {} notifiche non lette per utente ID: {}", notifications.size(), userId);
        return notifications;
    }

    public Long getUnreadNotificationCount(Long userId) {
        logger.debug("INIZIO - Conteggio notifiche non lette per utente ID: {}", userId);
        Long count = notificationRepository.countByUserIdAndReadFalse(userId);
        logger.debug("FINE - Trovate {} notifiche non lette per utente ID: {}", count, userId);
        return count;
    }

    public Notification createNotification(Long userId, String type, String title, String message) {
        logger.debug("INIZIO - Creazione notifica per utente ID: {}, Tipo: {}, Titolo: {}", userId, type, title);
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        Notification savedNotification = notificationRepository.save(notification);
        logger.debug("FINE - Notifica creata con successo con ID: {}", savedNotification.getId());
        return savedNotification;
    }

    public Notification createBookingCancelledNotification(Long userId, Long bookingId, 
            String roomName, String adminName, String bookingDate, String startTime, String endTime, String reason) {
        
        logger.debug("INIZIO - Creazione notifica di cancellazione per utente ID: {}, Prenotazione ID: {}", userId, bookingId);

        String title = "Cancellazione Prenotazione: " + roomName;
        String message;

        if (adminName != null) {
            message = String.format(
                "La tua prenotazione per la stanza '%s' il %s dalle %s alle %s è stata cancellata dall'amministratore %s.",
                roomName, bookingDate, startTime, endTime, adminName
            );
            if (reason != null && !reason.trim().isEmpty()) {
                message += " Motivo: " + reason;
            }
        } else {
            message = String.format(
                "Hai annullato la tua prenotazione per la stanza '%s' il %s dalle %s alle %s.",
                roomName, bookingDate, startTime, endTime
            );
        }
        
        Notification notification = createNotification(userId, "cancellazione", title, message);

        // Queste quattro colonne esistevano gia' sull'entita' ma NESSUNO le valorizzava:
        // erano permanentemente null da prima della separazione in servizi. Sono le uniche
        // che permettono al frontend di collegare la notifica alla prenotazione senza
        // interpretare il testo del messaggio, quindi vanno riempite.
        notification.setBookingId(bookingId);
        notification.setRoomName(roomName);
        notification.setAdminName(adminName);
        notification.setBookingDate(componiIstante(bookingDate, startTime));
        notification = notificationRepository.save(notification);
        logger.debug("FINE - Notifica di cancellazione creata con ID: {}", notification.getId());
        return notification;
    }

    public Optional<Notification> getNotificationById(Long notificationId) {
        logger.debug("INIZIO - Recupero notifica per ID: {}", notificationId);
        Optional<Notification> notification = notificationRepository.findById(notificationId);
        if (notification.isPresent()) {
            logger.debug("FINE - Notifica trovata con ID: {}", notificationId);
        } else {
            logger.warn("FINE - Notifica non trovata con ID: {}", notificationId);
        }
        return notification;
    }

    public Optional<Notification> markAsRead(Long notificationId, Long userId) {
        logger.debug("INIZIO - Tentativo di segnare notifica ID: {} come letta per utente ID: {}", notificationId, userId);
        Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
        
        if (notificationOpt.isPresent()) {
            Notification notification = notificationOpt.get();
            // Verifica che la notifica appartenga all'utente corretto
            if (notification.getUserId().equals(userId)) {
                notification.setRead(true);
                Notification updatedNotification = notificationRepository.save(notification);
                logger.debug("FINE - Notifica ID: {} segnata come letta.", notificationId);
                return Optional.of(updatedNotification);
            } else {
                logger.warn("FINE - Tentativo fallito. L'utente ID: {} non è autorizzato a modificare la notifica ID: {}", userId, notificationId);
                return Optional.empty(); // Utente non autorizzato
            }
        }
        
        logger.warn("FINE - Tentativo fallito. Notifica ID: {} non trovata.", notificationId);
        return Optional.empty(); // Notifica non trovata
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        logger.debug("INIZIO - Segna tutte le notifiche come lette per utente ID: {}", userId);
        notificationRepository.markAllAsRead(userId);
        logger.debug("FINE - Tutte le notifiche per utente ID: {} sono state segnate come lette.", userId);
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        logger.debug("INIZIO - Eliminazione notifica ID: {}", notificationId);
        notificationRepository.deleteById(notificationId);
        logger.debug("FINE - Eliminazione completata per notifica ID: {}", notificationId);
    }

    @Transactional
    public void deleteReadNotifications(Long userId) {
        logger.debug("INIZIO - Eliminazione notifiche lette per utente ID: {}", userId);
        notificationRepository.deleteByUserIdAndReadTrue(userId);
        logger.debug("FINE - Eliminazione notifiche lette completata per utente ID: {}", userId);
    }

    /**
     * Elimina tutte le notifiche di un utente, chiamato quando l'utente viene eliminato.
     *
     * Prima era una riga dentro la transazione di UtenteService, che cancellava notifiche,
     * prenotazioni e utente insieme. Ora e' un'operazione a se': se fallisce, l'utente puo'
     * risultare eliminato mentre le sue notifiche restano.
     */
    @Transactional
    public void deleteAllByUser(Long userId) {
        logger.info("Eliminazione di tutte le notifiche dell'utenteId={}", userId);
        notificationRepository.deleteByUserId(userId);
    }

    /**
     * Ricompone data e ora in un istante. Restituisce null invece di sollevare se il
     * formato non e' quello atteso: una notifica con un campo in meno resta utile, una
     * cancellazione che fallisce per un timestamp malformato no.
     */
    private static LocalDateTime componiIstante(String data, String now) {
        // I due null di questo metodo NON sono un segnale d'errore che attraversa un
        // confine: e' un helper privato di parsing, e null significa "non interpretabile",
        // che e' l'unico significato possibile. Convertirli in eccezioni farebbe fallire
        // la creazione di una notifica per una data malformata, quando la notifica ha
        // ancora senso senza quel campo.
        if (data == null || now == null) {
            return null;
        }
        try {
            return LocalDate.parse(data).atTime(LocalTime.parse(now));
        } catch (DateTimeParseException e) {
            logger.warn("Data prenotazione non interpretabile: data={} ora={}", data, now);
            return null;
        }
    }
}
