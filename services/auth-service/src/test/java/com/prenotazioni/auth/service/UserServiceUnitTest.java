package com.prenotazioni.auth.service;

import com.prenotazioni.auth.client.UserDataClient;
import com.prenotazioni.auth.repository.UserRepository;
import com.prenotazioni.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting a user and their data in the other services.
 *
 * Deleting a user has to take their bookings and their notifications with it, but those live
 * in two other databases behind two other services: there is no transaction covering all
 * three, and each piece can fail on its own.
 *
 * There is ONE invariant that makes this acceptable, and it is the one the tests below hold
 * still: the user is deleted LAST. While the user is still there, the rows left elsewhere
 * still have an owner they can be traced back to, and the operation can be repeated.
 * Deleting the user first would leave data nobody can attribute any more - and no amount
 * of repeating would put that right.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDataClient userDataClient;

    private UserService service() {
        return new UserService(userRepository, userDataClient);
    }

    @Test
    void deletesTheUserWhenTheCascadeSucceeded() {
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of());

        service().deleteById(7L);

        verify(userRepository).deleteById(7L);
    }

    @Test
    void doesNotDeleteTheUserWhenSomethingWasLeftBehind() {
        // The invariant. If it fell, a downstream failure would leave bookings and
        // notifications with no user to trace them back to, and repeating the operation
        // would achieve nothing: there would be nobody left to start from.
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of("bookings"));

        assertThatThrownBy(() -> service().deleteById(7L))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void theErrorSaysWhatWasLeftBehind() {
        // "Something failed" is not enough for whoever has to decide whether to retry: the
        // message has to name the data left behind, otherwise the only way to know is to
        // read the logs of three different services.
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of("notifications", "bookings"));

        assertThatThrownBy(() -> service().deleteById(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("notifications")
                .hasMessageContaining("bookings");
    }

    @Test
    void theErrorInvitesARetry() {
        // The user message and the code are what separates "it is broken" from "try
        // again". Those are two different actions, and with a generic 500 the second did not
        // come to mind: the operation stayed half done because nobody repeated it.
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of("notifications"));

        ServiceUnavailableException error = (ServiceUnavailableException)
                org.assertj.core.api.Assertions.catchThrowable(() -> service().deleteById(7L));

        assertThat(error.getErrorCode()).isEqualTo("USER_DELETE_INCOMPLETE");
        assertThat(error.getUserMessage()).containsIgnoringCase("riprova");
    }

    @Test
    void theCascadeRunsBeforeTheDeletion() {
        // The order, not just the outcome: the downstream data is ALWAYS attempted, even
        // when everything then goes well. If somebody one day swapped the two lines, the
        // other tests would carry on passing while the invariant was already lost.
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of());

        service().deleteById(7L);

        var order = org.mockito.Mockito.inOrder(userDataClient, userRepository);
        order.verify(userDataClient).deleteDataOf(7L);
        order.verify(userRepository).deleteById(7L);
    }
}
