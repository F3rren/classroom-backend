package com.classroom.notification.controller;

import com.classroom.dto.CountResponse;
import com.classroom.dto.MessageResponse;
import com.classroom.notification.model.Notification;
import com.classroom.security.AppPrincipal;
import com.classroom.notification.service.NotificationService;

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

/**
 * The notification endpoints the frontend calls.
 *
 * The MessageResponse texts stay Italian on purpose: unlike the internal controller next to
 * this one, whose caller is another service, these are read by the person in front of the
 * screen. Everything else here - Swagger summaries, log lines - is read by a programmer.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Notifications of the authenticated user")
    public ResponseEntity<List<Notification>> getNotifications(@AuthenticationPrincipal AppPrincipal principal) {
        return ResponseEntity.ok(notificationService.getNotificationsByUser(principal.id()));
    }

    @GetMapping("/unread")
    @Operation(summary = "Unread notifications of the authenticated user")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@AuthenticationPrincipal AppPrincipal principal) {
        return ResponseEntity.ok(notificationService.getUnreadNotificationsByUser(principal.id()));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count of unread notifications")
    public ResponseEntity<CountResponse> getUnreadNotificationCount(@AuthenticationPrincipal AppPrincipal principal) {
        Long count = notificationService.getUnreadNotificationCount(principal.id());
        return ResponseEntity.ok(new CountResponse(count));
    }

    @PutMapping("/{id}/mark-read")
    @Operation(summary = "Mark one notification as read")
    public ResponseEntity<Notification> markAsRead(@PathVariable("id") Long id, @AuthenticationPrincipal AppPrincipal principal) {
        logger.debug("START - request to mark notification as read, ID: {}", id);
        Optional<Notification> updatedNotificationOpt = notificationService.markAsRead(id, principal.id());

        if (updatedNotificationOpt.isPresent()) {
            logger.debug("END - notification ID: {} marked as read.", id);
            return ResponseEntity.ok(updatedNotificationOpt.get());
        }
        // The service has already logged why (not found, or not the owner).
        logger.warn("END - could not mark notification ID: {} as read: it may not exist, or the caller is not its owner.", id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @PutMapping("/mark-all-read")
    @Operation(summary = "Mark every notification as read")
    public ResponseEntity<MessageResponse> markAllAsRead(@AuthenticationPrincipal AppPrincipal principal) {
        notificationService.markAllAsRead(principal.id());
        logger.debug("Notifications marked as read for userId={}", principal.id());
        return ResponseEntity.ok(new MessageResponse("Tutte le notifiche sono state segnate come lette"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of your own notifications")
    public ResponseEntity<MessageResponse> deleteNotification(@PathVariable("id") Long id, @AuthenticationPrincipal AppPrincipal principal) {
        logger.debug("START - request to delete notification ID: {}", id);
        Optional<Notification> notificationOpt = notificationService.getNotificationById(id);
        if (notificationOpt.isEmpty()) {
            logger.warn("Notification to delete not found, ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Notification notification = notificationOpt.get();
        if (!notification.getUserId().equals(principal.id())) {
            logger.warn("userId={} is not allowed to delete notification {}", principal.id(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        notificationService.deleteNotification(id);
        logger.debug("END - notification ID: {} deleted.", id);
        return ResponseEntity.ok(new MessageResponse("Notifica eliminata con successo"));
    }

    @DeleteMapping("/read")
    @Operation(summary = "Delete every notification already read")
    public ResponseEntity<MessageResponse> deleteReadNotifications(@AuthenticationPrincipal AppPrincipal principal) {
        notificationService.deleteReadNotifications(principal.id());
        return ResponseEntity.ok(new MessageResponse("Notifiche lette eliminate con successo"));
    }
}
