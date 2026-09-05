package com.prenotazioni.auth.service;

import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.auth.dto.CreateUserRequest;
import com.prenotazioni.auth.dto.UpdateUserRequest;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.model.Role;
import com.prenotazioni.auth.repository.UserRepository;
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
 * I test HTTP fanno sempre login con credenziali corrette e registrano sempre utenti nuovi:
 * i rami di rifiuto di AuthService restavano quindi scoperti. Qui si coprono direttamente.
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

    private CreateUserRequest creazione(String email, String username) {
        CreateUserRequest r = new CreateUserRequest();
        r.setEmail(email);
        r.setUsername(username);
        r.setPassword("password123");
        r.setName("Nuovo Utente");
        r.setRole("user"); // DTO di richiesta: resta String, validata da @Pattern
        return r;
    }

    private UpdateUserRequest modifica(String email, String username, String password) {
        UpdateUserRequest r = new UpdateUserRequest();
        r.setEmail(email);
        r.setUsername(username);
        r.setPassword(password);
        r.setName("Nome Aggiornato");
        r.setRole("user"); // DTO di richiesta: resta String, validata da @Pattern
        return r;
    }

    // ==================== login ====================

    @Test
    void loginTornaNullSeLEmailNonEsiste() {
        when(userRepository.findByEmail("assente@test.it")).thenReturn(null);

        assertThat(service.login("assente@test.it", "qualsiasi")).isNull();
    }

    @Test
    void loginTornaNullSeLaPasswordNonCorrisponde() {
        when(userRepository.findByEmail("u@test.it")).thenReturn(user(1L, "u@test.it"));
        when(passwordEncoder.matches("sbagliata", "hash")).thenReturn(false);

        assertThat(service.login("u@test.it", "sbagliata")).isNull();
        // un login fallito non deve aggiornare l'ultimo accesso
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginRiuscitoRegistraLUltimoAccesso() {
        User u = user(1L, "u@test.it");
        u.setLastLogin(null);
        when(userRepository.findByEmail("u@test.it")).thenReturn(u);
        when(passwordEncoder.matches("giusta", "hash")).thenReturn(true);

        User loggato = service.login("u@test.it", "giusta");

        assertThat(loggato).isSameAs(u);
        assertThat(u.getLastLogin()).isNotNull();
        verify(userRepository).save(u);
    }

    // ==================== register ====================

    @Test
    void registerSegnalaEmailGiaRegistrata() {
        when(userRepository.findByEmail("gia@test.it")).thenReturn(user(1L, "gia@test.it"));

        assertThatThrownBy(() -> service.register(creazione("gia@test.it", "nuovo")))
                .isInstanceOf(DomainConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerSegnalaUsernameGiaRegistrato() {
        when(userRepository.findByEmail("nuova@test.it")).thenReturn(null);
        when(userRepository.findByUsername("occupato")).thenReturn(user(2L, "altro@test.it"));

        assertThatThrownBy(() -> service.register(creazione("nuova@test.it", "occupato")))
                .isInstanceOf(DomainConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerHashesThePasswordAndSetsRegistrationDate() {
        when(userRepository.findByEmail(anyString())).thenReturn(null);
        when(userRepository.findByUsername(anyString())).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("hash-calcolato");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(5L);
            return u;
        });

        User created = service.register(creazione("nuova@test.it", "nuovo"));

        assertThat(created).isNotNull();
        // la password non deve mai essere salvata in chiaro
        assertThat(created.getPassword()).isEqualTo("hash-calcolato");
        assertThat(created.getRegisteredAt()).isNotNull();
    }

    // ==================== getAllUsers ====================

    @Test
    void getAllUsersDelegatesToRepository() {
        when(userRepository.findAll()).thenReturn(List.of(user(1L, "a@test.it"), user(2L, "b@test.it")));

        assertThat(service.getAllUsers()).hasSize(2);
    }

    // ==================== updateUtente ====================

    @Test
    void updateSegnalaUtenteInesistente() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUser(9L, modifica("x@test.it", "x", "")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSegnalaEmailDiUnAltroUtente() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "mia@test.it")));
        when(userRepository.findByEmail("altrui@test.it")).thenReturn(user(2L, "altrui@test.it"));

        assertThatThrownBy(() -> service.updateUser(1L, modifica("altrui@test.it", "mio", "")))
                .isInstanceOf(DomainConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateSegnalaUsernameDiUnAltroUtente() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "mia@test.it")));
        when(userRepository.findByEmail("mia@test.it")).thenReturn(user(1L, "mia@test.it"));
        when(userRepository.findByUsername("altrui")).thenReturn(user(2L, "altro@test.it"));

        assertThatThrownBy(() -> service.updateUser(1L, modifica("mia@test.it", "altrui", "")))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void updateKeepsExistingPasswordWhenBlank() {
        User esistente = user(1L, "mia@test.it");
        when(userRepository.findById(1L)).thenReturn(Optional.of(esistente));
        when(userRepository.findByEmail("mia@test.it")).thenReturn(esistente);
        when(userRepository.findByUsername("mio")).thenReturn(esistente);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateUser(1L, modifica("mia@test.it", "mio", "   "));

        assertThat(esistente.getPassword()).isEqualTo("hash");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void laModificaRicifraLaPasswordSeIndicata() {
        User esistente = user(1L, "mia@test.it");
        when(userRepository.findById(1L)).thenReturn(Optional.of(esistente));
        when(userRepository.findByEmail("mia@test.it")).thenReturn(esistente);
        when(userRepository.findByUsername("mio")).thenReturn(esistente);
        when(passwordEncoder.encode("nuova-password")).thenReturn("nuovo-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateUser(1L, modifica("mia@test.it", "mio", "nuova-password"));

        assertThat(esistente.getPassword()).isEqualTo("nuovo-hash");
    }

    @Test
    void updateFallsBackToExistingRoleWhenNoneGiven() {
        User esistente = user(1L, "mia@test.it");
        esistente.setRole(Role.ADMIN);
        UpdateUserRequest request = modifica("mia@test.it", "mio", "");
        request.setRole(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(esistente));
        when(userRepository.findByEmail("mia@test.it")).thenReturn(esistente);
        when(userRepository.findByUsername("mio")).thenReturn(esistente);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateUser(1L, request);

        assertThat(esistente.getRole()).isEqualTo(Role.ADMIN);
    }
}
