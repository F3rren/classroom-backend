package com.classroom.notification.controller;

import com.classroom.dto.MessageResponse;
import com.classroom.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The endpoints this service exposes to OTHER SERVICES, not to the frontend.
 *
 * They used to be method calls inside the same process: AdminController invoked
 * NotificationService after cancelling a booking, and UserService deleted the notifications
 * of a removed user. Now they are network boundaries.
 *
 * There is no new authentication mechanism: the caller forwards the JWT of the administrator
 * who started the operation, and this service verifies it on its own like any other request.
 * Both operations are administrative actions by definition, so hasRole('ADMIN') is the
 * correct check and not a fallback.
 */
@RestController
@RequestMapping("/api/notifications/internal")
@Tag(name = "Notifications (internal)", description = "Called by other services, not by the frontend")
public class InternalNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(InternalNotificationController.class);

    private final NotificationService notificationService;

    InternalNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // The POST /booking-cancellation endpoint is gone: that notification now arrives as a
    // message on the queue, handled by CancellationListener. A REST call was lost if this
    // service was down; a queued message waits for it.
    //
    // This data-deletion endpoint stays synchronous on purpose: its caller (auth-service)
    // has to know whether it succeeded, because if it fails it does not delete the user.
    // A queue would give that guarantee away.

    /**
     * Deletes every notification of a deleted user.
     *
     * This used to be part of the same transaction that removed the user and their bookings;
     * now it is a separate call, so the operation as a whole is no longer atomic. If it
     * fails, orphan notifications are left behind: that is the cost of the split, and it
     * will be addressed with an event once there is a broker.
     */
    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete every notification of a user")
    public ResponseEntity<MessageResponse> deleteUserNotifications(@PathVariable("userId") Long userId) {
        logger.info("Deleting every notification of userId={}", userId);
        notificationService.deleteAllByUser(userId);
        // The caller is a service, not a person: this message is read in a log, so English.
        return ResponseEntity.ok(new MessageResponse("User notifications deleted"));
    }
}
