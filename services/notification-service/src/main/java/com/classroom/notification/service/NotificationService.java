package com.classroom.notification.service;

import com.classroom.notification.model.Notification;
import com.classroom.notification.repository.NotificationRepository;
import org.springframework.lang.NonNull;
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

    /** The type stored on the notification, and what a client filters on. */
    private static final String TYPE_CANCELLATION = "cancellation";

    private final NotificationRepository notificationRepository;

    NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getNotificationsByUser(Long userId) {
        logger.debug("START - fetching notifications for user ID: {}", userId);
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        logger.debug("END - fetched {} notifications for user ID: {}", notifications.size(), userId);
        return notifications;
    }

    public List<Notification> getUnreadNotificationsByUser(Long userId) {
        logger.debug("START - fetching unread notifications for user ID: {}", userId);
        List<Notification> notifications = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        logger.debug("END - fetched {} unread notifications for user ID: {}", notifications.size(), userId);
        return notifications;
    }

    public Long getUnreadNotificationCount(Long userId) {
        logger.debug("START - counting unread notifications for user ID: {}", userId);
        Long count = notificationRepository.countByUserIdAndReadFalse(userId);
        logger.debug("END - found {} unread notifications for user ID: {}", count, userId);
        return count;
    }

    public Notification createNotification(Long userId, String type, String title, String message) {
        logger.debug("START - creating notification for user ID: {}, type: {}, title: {}", userId, type, title);
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);

        Notification savedNotification = notificationRepository.save(notification);
        logger.debug("END - notification created with ID: {}", savedNotification.getId());
        return savedNotification;
    }

    /**
     * The notification a user receives when a booking of theirs is cancelled.
     *
     * The title and the message stay Italian: they are the one thing here that a person
     * reads, straight out of the frontend.
     *
     * roomName and adminName arrive null when the room was since deleted or the admin's
     * token carried no name - booking-service used to paper over that with its own Italian
     * placeholder text before publishing the event. That was this service's call to make,
     * not booking-service's: the wording shown to the recipient belongs to whoever owns
     * that wording, so the two fall back here instead. The only caller of this method is
     * the admin-cancellation flow (there is no self-cancellation notification today), which
     * is why a missing admin name reads as "an administrator", never as "you".
     */
    public Notification createBookingCancelledNotification(Long userId, Long bookingId,
            String roomName, String adminName, String bookingDate, String startTime, String endTime, String reason) {

        logger.debug("START - creating cancellation notification for user ID: {}, booking ID: {}", userId, bookingId);

        String room = roomName != null ? roomName : "una stanza non più disponibile";
        String admin = adminName != null ? adminName : "un amministratore";

        String title = "Cancellazione Prenotazione: " + room;
        String message = String.format(
            "La tua prenotazione per la stanza '%s' il %s dalle %s alle %s è stata cancellata da %s.",
            room, bookingDate, startTime, endTime, admin
        );
        if (reason != null && !reason.trim().isEmpty()) {
            message += " Motivo: " + reason;
        }

        Notification notification = createNotification(userId, TYPE_CANCELLATION, title, message);

        // These four columns already existed on the entity but NOBODY was filling them in:
        // they had been permanently null since before the split into services. They are the
        // only thing letting the frontend tie the notification back to the booking without
        // parsing the message text, so they get filled. The defaulted room/admin go in here
        // too, so the structured fields and the message text never disagree.
        notification.setBookingId(bookingId);
        notification.setRoomName(room);
        notification.setAdminName(admin);
        notification.setBookingDate(toInstant(bookingDate, startTime));
        notification = notificationRepository.save(notification);
        logger.debug("END - cancellation notification created with ID: {}", notification.getId());
        return notification;
    }

    public Optional<Notification> getNotificationById(@NonNull Long notificationId) {
        logger.debug("START - fetching notification by ID: {}", notificationId);
        Optional<Notification> notification = notificationRepository.findById(notificationId);
        if (notification.isPresent()) {
            logger.debug("END - notification found with ID: {}", notificationId);
        } else {
            logger.warn("END - no notification found with ID: {}", notificationId);
        }
        return notification;
    }

    public Optional<Notification> markAsRead(@NonNull Long notificationId, Long userId) {
        logger.debug("START - marking notification ID: {} as read for user ID: {}", notificationId, userId);
        Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);

        if (notificationOpt.isPresent()) {
            Notification notification = notificationOpt.get();
            // The notification has to belong to the user asking.
            if (notification.getUserId().equals(userId)) {
                notification.setRead(true);
                Notification updatedNotification = notificationRepository.save(notification);
                logger.debug("END - notification ID: {} marked as read.", notificationId);
                return Optional.of(updatedNotification);
            } else {
                logger.warn("END - refused. User ID: {} is not allowed to change notification ID: {}", userId, notificationId);
                return Optional.empty(); // not the owner
            }
        }

        logger.warn("END - refused. Notification ID: {} not found.", notificationId);
        return Optional.empty(); // no such notification
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        logger.debug("START - marking every notification as read for user ID: {}", userId);
        notificationRepository.markAllAsRead(userId);
        logger.debug("END - every notification for user ID: {} has been marked as read.", userId);
    }

    @Transactional
    public void deleteNotification(@NonNull Long notificationId) {
        logger.debug("START - deleting notification ID: {}", notificationId);
        notificationRepository.deleteById(notificationId);
        logger.debug("END - deletion complete for notification ID: {}", notificationId);
    }

    @Transactional
    public void deleteReadNotifications(Long userId) {
        logger.debug("START - deleting read notifications for user ID: {}", userId);
        notificationRepository.deleteByUserIdAndReadTrue(userId);
        logger.debug("END - read notifications deleted for user ID: {}", userId);
    }

    /**
     * Deletes every notification of a user, called when that user is deleted.
     *
     * This used to be one line inside UserService's transaction, which removed notifications,
     * bookings and the user together. Now it is an operation of its own: if it fails, the
     * user can end up deleted while their notifications stay behind.
     */
    @Transactional
    public void deleteAllByUser(Long userId) {
        logger.info("Deleting every notification of userId={}", userId);
        notificationRepository.deleteByUserId(userId);
    }

    /**
     * Recomposes a date and a time of day into an instant. Returns null rather than throwing
     * when the format is not the expected one: a notification missing one field is still
     * useful, a cancellation that fails over a malformed timestamp is not.
     */
    private static LocalDateTime toInstant(String date, String time) {
        // The two nulls in this method are NOT an error signal crossing a boundary: this is a
        // private parsing helper, and null means "not interpretable", which is the only
        // meaning available. Turning them into exceptions would fail the creation of a
        // notification over a malformed date, when the notification still makes sense
        // without that field.
        if (date == null || time == null) {
            return null;
        }
        try {
            return LocalDate.parse(date).atTime(LocalTime.parse(time));
        } catch (DateTimeParseException e) {
            logger.warn("Booking date not interpretable: date={} time={}", date, time);
            return null;
        }
    }
}
