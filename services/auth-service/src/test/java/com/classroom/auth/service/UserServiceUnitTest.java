package com.classroom.auth.service;

import com.classroom.auth.messaging.EventPublisher;
import com.classroom.auth.repository.UserRepository;
import com.classroom.events.UserDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting a user, and telling booking-service and notification-service their data on that
 * user is now orphaned.
 *
 * The two services no longer hear about it over REST: they consume a UserDeletedEvent
 * published after the user row is gone. That order is the point of the tests below - it is
 * the same order booking-service already uses for a cancelled booking, and for the same
 * reason: the user has already been deleted, and a failed publish must not undo that or
 * block the admin's response.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventPublisher eventPublisher;

    private UserService service() {
        return new UserService(userRepository, eventPublisher);
    }

    @Test
    void deletesTheUserAndPublishesTheEvent() {
        service().deleteById(7L);

        verify(userRepository).deleteById(7L);
        verify(eventPublisher).publishUserDeleted(eq(new UserDeletedEvent(7L)));
    }

    @Test
    void theDeletionRunsBeforeThePublish() {
        // The order, not just the outcome: the user has to be gone before anybody is told so.
        // If somebody one day swapped the two lines, the other test would carry on passing
        // while the invariant behind the design was already lost.
        service().deleteById(7L);

        var order = org.mockito.Mockito.inOrder(userRepository, eventPublisher);
        order.verify(userRepository).deleteById(7L);
        order.verify(eventPublisher).publishUserDeleted(eq(new UserDeletedEvent(7L)));
    }

    @Test
    void aFailedPublishDoesNotUndoTheDeletion() {
        // The user is already gone by the time the publish is attempted: failing the
        // response because the broker is unreachable would be worse than the damage. This
        // mirrors EventPublisherUnitTest.anUnreachableBrokerDoesNotFailTheCancellation in
        // booking-service.
        when(eventPublisher.publishUserDeleted(eq(new UserDeletedEvent(7L)))).thenReturn(false);

        service().deleteById(7L);

        verify(userRepository).deleteById(7L);
    }
}
