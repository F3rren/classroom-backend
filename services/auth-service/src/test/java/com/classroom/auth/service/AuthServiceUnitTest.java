package com.classroom.auth.service;

import com.classroom.exception.DomainConflictException;
import com.classroom.exception.ResourceNotFoundException;
import com.classroom.auth.dto.CreateUserRequest;
import com.classroom.auth.dto.UpdateUserRequest;
import com.classroom.auth.model.User;
import com.classroom.model.Role;
import com.classroom.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The HTTP tests always log in with correct credentials and always register new users, so
 * AuthService's rejection branches were left uncovered. They are covered directly here.
 */
class AuthServiceUnitTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthService(userRepository, passwordEncoder);
    }

    private User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setUsername("utente" + id);
        u.setName("Nome " + id);
        u.setRole(Role.USER);
        u.setPassword("hash");
        u.setRegisteredAt(LocalDateTime.now());
        return u;
    }

    private CreateUserRequest creation(String email, String username) {
        // DTO di richiesta: role resta String, validata da @Pattern
        return new CreateUserRequest(username, email, "password123", "user", "Nuovo Utente");
    }

    private UpdateUserRequest updateRequest(String email, String username, String password) {
        return updateRequest(email, username, password, "user");
    }

    private UpdateUserRequest updateRequest(String email, String username, String password, String role) {
        // DTO di richiesta: role resta String, validata da @Pattern
        return new UpdateUserRequest(username, email, password, role, "Nome Aggiornato");
    }

    // ==================== login ====================

    @Test
    void loginReturnsNullWhenTheEmailDoesNotExist() {
        when(userRepository.findByEmail("assente@test.it")).thenReturn(null);

        assertThat(service.login("assente@test.it", "qualsiasi")).isNull();
    }

    @Test
    @SuppressWarnings("null")
    void loginReturnsNullWhenThePasswordDoesNotMatch() {
        when(userRepository.findByEmail("u@test.it")).thenReturn(user(1L, "u@test.it"));
        when(passwordEncoder.matches("sbagliata", "hash")).thenReturn(false);

        assertThat(service.login("u@test.it", "sbagliata")).isNull();
        // a failed login must not update the last login timestamp
        verify(userRepository, never()).save(any());
    }

    @Test
    void aSuccessfulLoginRecordsTheLastLogin() {
        User u = user(1L, "u@test.it");
        u.setLastLogin(null);
        when(userRepository.findByEmail("u@test.it")).thenReturn(u);
        when(passwordEncoder.matches("giusta", "hash")).thenReturn(true);

        User loggedIn = service.login("u@test.it", "giusta");

        assertThat(loggedIn).isSameAs(u);
        assertThat(u.getLastLogin()).isNotNull();
        verify(userRepository).save(u);
    }

    // ==================== register ====================

    @Test
    @SuppressWarnings("null")
    void registerReportsAnAlreadyRegisteredEmail() {
        when(userRepository.findByEmail("gia@test.it")).thenReturn(user(1L, "gia@test.it"));

        assertThatThrownBy(() -> service.register(creation("gia@test.it", "nuovo")))
                .isInstanceOf(DomainConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void registerReportsAnAlreadyRegisteredUsername() {
        when(userRepository.findByEmail("nuova@test.it")).thenReturn(null);
        when(userRepository.findByUsername("occupato")).thenReturn(user(2L, "altro@test.it"));

        assertThatThrownBy(() -> service.register(creation("nuova@test.it", "occupato")))
                .isInstanceOf(DomainConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void registerHashesThePasswordAndSetsRegistrationDate() {
        when(userRepository.findByEmail(anyString())).thenReturn(null);
        when(userRepository.findByUsername(anyString())).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("hash-calcolato");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(5L);
            return u;
        });

        User created = service.register(creation("nuova@test.it", "nuovo"));

        assertThat(created).isNotNull();
        // the password must never be stored in the clear
        assertThat(created.getPassword()).isEqualTo("hash-calcolato");
        assertThat(created.getRegisteredAt()).isNotNull();
    }

    // ==================== getAllUsers ====================

    @Test
    void getAllUsersDelegatesToRepository() {
        when(userRepository.findAll()).thenReturn(List.of(user(1L, "a@test.it"), user(2L, "b@test.it")));

        assertThat(service.getAllUsers()).hasSize(2);
    }

    // ==================== updateUser ====================

    @Test
    void updateReportsAMissingUser() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUser(9L, updateRequest("x@test.it", "x", "")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @SuppressWarnings("null")
    void updateReportsAnEmailBelongingToAnotherUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "mia@test.it")));
        when(userRepository.findByEmail("altrui@test.it")).thenReturn(user(2L, "altrui@test.it"));

        assertThatThrownBy(() -> service.updateUser(1L, updateRequest("altrui@test.it", "mio", "")))
                .isInstanceOf(DomainConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateReportsAUsernameBelongingToAnotherUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "mia@test.it")));
        when(userRepository.findByEmail("mia@test.it")).thenReturn(user(1L, "mia@test.it"));
        when(userRepository.findByUsername("altrui")).thenReturn(user(2L, "altro@test.it"));

        assertThatThrownBy(() -> service.updateUser(1L, updateRequest("mia@test.it", "altrui", "")))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    @SuppressWarnings("null")
    void updateKeepsExistingPasswordWhenBlank() {
        User existing = user(1L, "mia@test.it");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("mia@test.it")).thenReturn(existing);
        when(userRepository.findByUsername("mio")).thenReturn(existing);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateUser(1L, updateRequest("mia@test.it", "mio", "   "));

        assertThat(existing.getPassword()).isEqualTo("hash");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @SuppressWarnings("null")
    void theUpdateRehashesThePasswordWhenOneIsGiven() {
        User existing = user(1L, "mia@test.it");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("mia@test.it")).thenReturn(existing);
        when(userRepository.findByUsername("mio")).thenReturn(existing);
        when(passwordEncoder.encode("nuova-password")).thenReturn("nuovo-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateUser(1L, updateRequest("mia@test.it", "mio", "nuova-password"));

        assertThat(existing.getPassword()).isEqualTo("nuovo-hash");
    }

    @Test
    void updateFallsBackToExistingRoleWhenNoneGiven() {
        User existing = user(1L, "mia@test.it");
        existing.setRole(Role.ADMIN);
        UpdateUserRequest request = updateRequest("mia@test.it", "mio", "", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("mia@test.it")).thenReturn(existing);
        when(userRepository.findByUsername("mio")).thenReturn(existing);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateUser(1L, request);

        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
    }
}
