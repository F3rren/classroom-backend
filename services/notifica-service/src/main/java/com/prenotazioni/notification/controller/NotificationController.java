package com.prenotazioni.notification.controller;

import com.prenotazioni.dto.CountResponse;
import com.prenotazioni.dto.MessageResponse;
import com.prenotazioni.notification.model.Notification;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifiche")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Notifiche dell'utente autenticato")
    public ResponseEntity<List<Notification>> getNotifications(@AuthenticationPrincipal AppPrincipal principal) {
        return ResponseEntity.ok(notificationService.getNotificationsByUser(principal.id()));
    }

    @GetMapping("/unread")
    @Operation(summary = "Notifiche non lette dell'utente autenticato")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@AuthenticationPrincipal AppPrincipal principal) {
        return ResponseEntity.ok(notificationService.getUnreadNotificationsByUser(principal.id()));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Conteggio notifiche non lette")
    public ResponseEntity<CountResponse> getUnreadNotificationCount(@AuthenticationPrincipal AppPrincipal principal) {
        Long count = notificationService.getUnreadNotificationCount(principal.id());
        return ResponseEntity.ok(new CountResponse(count));
    }

    @PutMapping("/{id}/mark-read")
    @Operation(summary = "Segna una notifica come letta")
    public ResponseEntity<Notification> markAsRead(@PathVariable("id") Long id, @AuthenticationPrincipal AppPrincipal principal) {
        logger.debug("INIZIO - Richiesta di segnare notifica come letta, ID: {}", id);
        Optional<Notification> updatedNotificationOpt = notificationService.markAsRead(id, principal.id());

        if (updatedNotificationOpt.isPresent()) {
            logger.debug("FINE - Notifica ID: {} segnata come letta con successo.", id);
            return ResponseEntity.ok(updatedNotificationOpt.get());
        }
        // Il service ha gia' loggato il motivo (notifica non trovata o non autorizzato)
        logger.warn("FINE - Impossibile segnare notifica ID: {} come letta. Potrebbe non esistere o l'utente non è autorizzato.", id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @PutMapping("/mark-all-read")
    @Operation(summary = "Segna tutte le notifiche come lette")
    public ResponseEntity<MessageResponse> markAllAsRead(@AuthenticationPrincipal AppPrincipal principal) {
        notificationService.markAllAsRead(principal.id());
        logger.debug("Notifiche segnate come lette per utenteId={}", principal.id());
        return ResponseEntity.ok(new MessageResponse("Tutte le notifiche sono state segnate come lette"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina una notifica propria")
    public ResponseEntity<MessageResponse> deleteNotification(@PathVariable("id") Long id, @AuthenticationPrincipal AppPrincipal principal) {
        logger.debug("INIZIO - Richiesta di eliminazione notifica ID: {}", id);
        Optional<Notification> notificationOpt = notificationService.getNotificationById(id);
        if (notificationOpt.isEmpty()) {
            logger.warn("Notifica da eliminare non trovata, ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Notification notification = notificationOpt.get();
        if (!notification.getUserId().equals(principal.id())) {
            logger.warn("UtenteId={} non autorizzato a eliminare la notifica {}", principal.id(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        notificationService.deleteNotification(id);
        logger.debug("FINE - Notifica ID: {} eliminata con successo.", id);
        return ResponseEntity.ok(new MessageResponse("Notifica eliminata con successo"));
    }

    @DeleteMapping("/read")
    @Operation(summary = "Elimina tutte le notifiche gia' lette")
    public ResponseEntity<MessageResponse> deleteReadNotifications(@AuthenticationPrincipal AppPrincipal principal) {
        notificationService.deleteReadNotifications(principal.id());
        return ResponseEntity.ok(new MessageResponse("Notifiche lette eliminate con successo"));
    }
}
