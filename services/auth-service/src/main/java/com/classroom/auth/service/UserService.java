package com.classroom.auth.service;

import com.classroom.auth.model.User;
import com.classroom.auth.repository.UserRepository;
import com.classroom.auth.messaging.EventPublisher;
import com.classroom.events.UserDeletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;

    private final EventPublisher eventPublisher;

    UserService(UserRepository userRepository, EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public User findById(@NonNull Long id) {
        logger.debug("START - looking up user by ID: {}", id);
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            logger.debug("END - user found by ID: {}", id);
        } else {
            logger.warn("END - no user found with ID: {}", id);
        }
        return user;
    }

    /**
     * Deletes the user, then tells the other services their data is now orphaned.
     *
     * It is NOT atomic, and that is better learnt from reading this method than discovered
     * from inconsistent data. Before the split it was a single transaction and the foreign
     * keys guaranteed nothing was left orphaned; notifications and bookings now live in
     * databases this service cannot touch.
     *
     * @Transactional stays, but it covers only the row in this database.
     *
     * The order is deliberate, and it is the reverse of what it used to be: the user is
     * deleted FIRST, and the event is published best-effort afterwards, exactly as
     * booking-service already does for a cancelled booking - the user is already gone by
     * the time we get here, and failing this response would not bring them back. The
     * trade-off is real: if the broker is unreachable, the event never reaches the other two
     * services and their rows are never cleaned up. There is no longer a synchronous
     * guarantee that the whole deletion succeeded, the way there used to be.
     */
    @Transactional
    public void deleteById(@NonNull Long id) {
        logger.info("Deleting user ID: {}", id);
        userRepository.deleteById(id);
        eventPublisher.publishUserDeleted(new UserDeletedEvent(id));
        logger.debug("END - deletion complete for user ID: {}", id);
    }
}