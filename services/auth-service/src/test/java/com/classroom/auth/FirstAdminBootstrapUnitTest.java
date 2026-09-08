package com.classroom.auth;

import com.classroom.auth.dto.CreateUserRequest;
import com.classroom.auth.model.User;
import com.classroom.auth.repository.UserRepository;
import com.classroom.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The runner that creates the first administrator.
 *
 * This is code that manufactures a user with maximum privileges, so the guarantee that makes
 * it acceptable - it acts ONLY on an empty table - is not enough to write in a comment: it
 * has to be held still by a test that would fail if somebody loosened it. The first case
 * below is that test, and it is worth more than the others put together.
 */
@ExtendWith(MockitoExtension.class)
class FirstAdminBootstrapUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    private FirstAdminBootstrap runner(String email, String password) {
        return new FirstAdminBootstrap(userRepository, authService, email, password, "Amministratore");
    }

    @Test
    void touchesNothingWhenUsersAlreadyExist() {
        // THE guarantee. If this test fell, the class would stop being a help at startup and
        // become a shortcut to administrator privileges on a system in use. The credentials
        // passed here are deliberately valid: the point is that they are not used anyway.
        when(userRepository.count()).thenReturn(7L);

        runner("nuovo@admin.it", "unaPasswordValida1!").run(null);

        verifyNoInteractions(authService);
    }

    @Test
    void createsTheAdministratorOnAnEmptyDatabase() {
        when(userRepository.count()).thenReturn(0L);
        User created = new User();
        created.setId(1L);
        when(authService.register(any())).thenReturn(created);

        runner("primo@admin.it", "unaPasswordValida1!").run(null);

        ArgumentCaptor<CreateUserRequest> request = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(authService).register(request.capture());
        assertThat(request.getValue().getEmail()).isEqualTo("primo@admin.it");
        // The role is lowercase: it is the value of the Role enum, not the constant's name.
        // Getting it wrong would create an ordinary user and leave the knot tied, with no
        // error to show for it.
        assertThat(request.getValue().getRole()).isEqualTo("admin");
    }

    @Test
    void doesNothingWithoutCredentials() {
        // The normal case for anyone not using the mechanism: empty database, variables
        // unset. It has to be a non-event, not a failed startup.
        when(userRepository.count()).thenReturn(0L);

        runner("", "").run(null);

        verify(authService, never()).register(any());
    }

    @Test
    void oneOfTheTwoAloneIsNotEnough() {
        // Half a configuration is more likely than none - you set the email and forget the
        // password - and it must not produce an administrator with an empty password.
        when(userRepository.count()).thenReturn(0L);

        runner("primo@admin.it", "").run(null);
        runner("", "unaPasswordValida1!").run(null);

        verify(authService, never()).register(any());
    }
}
