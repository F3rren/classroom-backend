package com.classroom.notification.controller;

import com.classroom.dto.CountResponse;
import com.classroom.dto.MessageResponse;
import com.classroom.notification.model.Notification;
import com.classroom.security.AppPrincipal;
import com.classroom.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * The notification endpoints the frontend calls.
 *
 * The MessageResponse texts stay Italian on purpose: unlike the internal controller next to
 * this one, whose caller is another service, these are read by the person in front of the
 * screen. Everything else here - Swagger summaries, log lines - is read by a programmer.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // The list itself is unwrapped - a bare JSON array - exactly as it always was, so an
    // existing caller reading it that way keeps working. Only the EMPTY case changes: it used
    // to answer "[]" too, indistinguishable from a slow network or a client that read the
    // wrong field, where the rest of this codebase answers a MessageResponse instead (see
    // BookingController.getAllBookings, the same idiom for the same case).
    @GetMapping
    @Operation(summary = "Notifications of the authenticated user")
    @ApiResponse(responseCode = "200", description = "The caller's notifications, or a MessageResponse when there are none",
            content = @Content(schema = @Schema(implementation = Notification.class, type = "array")))
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal AppPrincipal principal) {
        List<Notification> notifications = notificationService.getNotificationsByUser(principal.id());
        if (notifications.isEmpty()) {
            return ResponseEntity.ok(new MessageResponse("Nessuna notifica trovata"));
        }
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread")
    @Operation(summary = "Unread notifications of the authenticated user")
    @ApiResponse(responseCode = "200", description = "The caller's unread notifications, or a MessageResponse when there are none",
            content = @Content(schema = @Schema(implementation = Notification.class, type = "array")))
    public ResponseEntity<?> getUnreadNotifications(@AuthenticationPrincipal AppPrincipal principal) {
        List<Notification> notifications = notificationService.getUnreadNotificationsByUser(principal.id());
        if (notifications.isEmpty()) {
            return ResponseEntity.ok(new MessageResponse("Nessuna notifica da leggere"));
        }
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count of unread notifications")
    public ResponseEntity<CountResponse> getUnreadNotificationCount(@AuthenticationPrincipal AppPrincipal principal) {
        Long count = notificationService.getUnreadNotificationCount(principal.id());
        return ResponseEntity.ok(new CountResponse(count));
    }

    @PutMapping("/{id}/mark-read")
    @Operation(summary = "Mark one notification as read")
    public ResponseEntity<Notification> markAsRead(@PathVariable("id") @NonNull Long id, @AuthenticationPrincipal AppPrincipal principal) {
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
    public ResponseEntity<MessageResponse> deleteNotification(@PathVariable("id") @NonNull Long id, @AuthenticationPrincipal AppPrincipal principal) {
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
