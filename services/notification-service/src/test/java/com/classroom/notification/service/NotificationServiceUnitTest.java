package com.classroom.notification.service;

import com.classroom.notification.model.Notification;
import com.classroom.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The wording of a cancellation notification when the room or the admin's name is missing.
 *
 * This used to be booking-service's decision - it defaulted both to Italian placeholder text
 * before publishing the event. Notification-service owns the wording shown to a person, so
 * the fallback moved here: booking-service now passes null when it does not know, and this
 * is where that turns into something readable.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceUnitTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service() {
        return new NotificationService(notificationRepository);
    }

    private void stubSaveToReturnItsArgument() {
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void fallsBackToAGenericRoomWhenNoneIsGiven() {
        stubSaveToReturnItsArgument();

        Notification notification = service().createBookingCancelledNotification(
                7L, 42L, null, "Mario Rossi", "2026-12-25", "14:30", "16:30", null);

        assertThat(notification.getRoomName()).isEqualTo("una stanza non più disponibile");
        assertThat(notification.getTitle()).contains("una stanza non più disponibile");
        assertThat(notification.getMessage()).contains("una stanza non più disponibile");
    }

    @Test
    void fallsBackToAGenericAdministratorWhenTheNameIsMissing() {
        stubSaveToReturnItsArgument();

        Notification notification = service().createBookingCancelledNotification(
                7L, 42L, "Aula Magna", null, "2026-12-25", "14:30", "16:30", null);

        assertThat(notification.getAdminName()).isEqualTo("un amministratore");
        // Not "you cancelled your own booking": the only caller of this method is the admin
        // path, so a missing name still reads as an admin, never as a self-cancellation.
        assertThat(notification.getMessage())
                .contains("cancellata da un amministratore")
                .doesNotContain("Hai annullato");
    }

    @Test
    void bothGivenValuesFlowThroughUnchanged() {
        stubSaveToReturnItsArgument();

        Notification notification = service().createBookingCancelledNotification(
                7L, 42L, "Aula Magna", "Mario Rossi", "2026-12-25", "14:30", "16:30", "Manutenzione");

        assertThat(notification.getRoomName()).isEqualTo("Aula Magna");
        assertThat(notification.getAdminName()).isEqualTo("Mario Rossi");
        assertThat(notification.getMessage())
                .contains("Aula Magna")
                .contains("cancellata da Mario Rossi")
                .contains("Motivo: Manutenzione");
    }
}
