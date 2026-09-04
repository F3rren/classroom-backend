package com.prenotazioni.auth.service;

import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.auth.dto.CreateUserRequest;
import com.prenotazioni.auth.dto.UpdateUserRequest;
import com.prenotazioni.auth.model.Utente;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.auth.repository.IUtenteRepository;
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

    private IUtenteRepository utenteRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        utenteRepository = mock(IUtenteRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthService(utenteRepository, passwordEncoder);
    }

    private Utente utente(Long id, String email) {
        Utente u = new Utente();
        u.setId(id);
        u.setEmail(email);
        u.setUsername("utente" + id);
        u.setNome("Nome " + id);
        u.setRuolo(Ruolo.USER);
        u.setPassword("hash");
        u.setDataRegistrazione(LocalDateTime.now());
        return u;
    }

    private CreateUserRequest creazione(String email, String username) {
        CreateUserRequest r = new CreateUserRequest();
        r.setEmail(email);
        r.setUsername(username);
        r.setPassword("password123");
        r.setNome("Nuovo Utente");
        r.setRuolo("user"); // DTO di richiesta: resta String, validata da @Pattern
        return r;
    }

    private UpdateUserRequest modifica(String email, String username, String password) {
        UpdateUserRequest r = new UpdateUserRequest();
        r.setEmail(email);
        r.setUsername(username);
        r.setPassword(password);
        r.setNome("Nome Aggiornato");
        r.setRuolo("user"); // DTO di richiesta: resta String, validata da @Pattern
        return r;
    }

    // ==================== login ====================

    @Test
    void loginTornaNullSeLEmailNonEsiste() {
        when(utenteRepository.findByEmail("assente@test.it")).thenReturn(null);

        assertThat(service.login("assente@test.it", "qualsiasi")).isNull();
    }

    @Test
    void loginTornaNullSeLaPasswordNonCorrisponde() {
        when(utenteRepository.findByEmail("u@test.it")).thenReturn(utente(1L, "u@test.it"));
        when(passwordEncoder.matches("sbagliata", "hash")).thenReturn(false);

        assertThat(service.login("u@test.it", "sbagliata")).isNull();
        // un login fallito non deve aggiornare l'ultimo accesso
        verify(utenteRepository, never()).save(any());
    }

    @Test
    void loginRiuscitoRegistraLUltimoAccesso() {
        Utente u = utente(1L, "u@test.it");
        u.setUltimoAccesso(null);
        when(utenteRepository.findByEmail("u@test.it")).thenReturn(u);
        when(passwordEncoder.matches("giusta", "hash")).thenReturn(true);

        Utente loggato = service.login("u@test.it", "giusta");

        assertThat(loggato).isSameAs(u);
        assertThat(u.getUltimoAccesso()).isNotNull();
        verify(utenteRepository).save(u);
    }

    // ==================== register ====================

    @Test
    void registerSegnalaEmailGiaRegistrata() {
        when(utenteRepository.findByEmail("gia@test.it")).thenReturn(utente(1L, "gia@test.it"));

        assertThatThrownBy(() -> service.register(creazione("gia@test.it", "nuovo")))
                .isInstanceOf(DomainConflictException.class);
        verify(utenteRepository, never()).save(any());
    }

    @Test
    void registerSegnalaUsernameGiaRegistrato() {
        when(utenteRepository.findByEmail("nuova@test.it")).thenReturn(null);
        when(utenteRepository.findByUsername("occupato")).thenReturn(utente(2L, "altro@test.it"));

        assertThatThrownBy(() -> service.register(creazione("nuova@test.it", "occupato")))
                .isInstanceOf(DomainConflictException.class);
        verify(utenteRepository, never()).save(any());
    }

    @Test
    void registerHashesThePasswordAndSetsRegistrationDate() {
        when(utenteRepository.findByEmail(anyString())).thenReturn(null);
        when(utenteRepository.findByUsername(anyString())).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("hash-calcolato");
        when(utenteRepository.save(any(Utente.class))).thenAnswer(inv -> {
            Utente u = inv.getArgument(0);
            u.setId(5L);
            return u;
        });

        Utente creato = service.register(creazione("nuova@test.it", "nuovo"));

        assertThat(creato).isNotNull();
        // la password non deve mai essere salvata in chiaro
        assertThat(creato.getPassword()).isEqualTo("hash-calcolato");
        assertThat(creato.getDataRegistrazione()).isNotNull();
    }

    // ==================== getAllUsers ====================

    @Test
    void getAllUsersDelegatesToRepository() {
        when(utenteRepository.findAll()).thenReturn(List.of(utente(1L, "a@test.it"), utente(2L, "b@test.it")));

        assertThat(service.getAllUsers()).hasSize(2);
    }

    // ==================== updateUtente ====================

    @Test
    void updateSegnalaUtenteInesistente() {
        when(utenteRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUtente(9L, modifica("x@test.it", "x", "")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSegnalaEmailDiUnAltroUtente() {
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "mia@test.it")));
        when(utenteRepository.findByEmail("altrui@test.it")).thenReturn(utente(2L, "altrui@test.it"));

        assertThatThrownBy(() -> service.updateUtente(1L, modifica("altrui@test.it", "mio", "")))
                .isInstanceOf(DomainConflictException.class);
        verify(utenteRepository, never()).save(any());
    }

    @Test
    void updateSegnalaUsernameDiUnAltroUtente() {
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "mia@test.it")));
        when(utenteRepository.findByEmail("mia@test.it")).thenReturn(utente(1L, "mia@test.it"));
        when(utenteRepository.findByUsername("altrui")).thenReturn(utente(2L, "altro@test.it"));

        assertThatThrownBy(() -> service.updateUtente(1L, modifica("mia@test.it", "altrui", "")))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void updateKeepsExistingPasswordWhenBlank() {
        Utente esistente = utente(1L, "mia@test.it");
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(esistente));
        when(utenteRepository.findByEmail("mia@test.it")).thenReturn(esistente);
        when(utenteRepository.findByUsername("mio")).thenReturn(esistente);
        when(utenteRepository.save(any(Utente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateUtente(1L, modifica("mia@test.it", "mio", "   "));

        assertThat(esistente.getPassword()).isEqualTo("hash");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void laModificaRicifraLaPasswordSeIndicata() {
        Utente esistente = utente(1L, "mia@test.it");
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(esistente));
        when(utenteRepository.findByEmail("mia@test.it")).thenReturn(esistente);
        when(utenteRepository.findByUsername("mio")).thenReturn(esistente);
        when(passwordEncoder.encode("nuova-password")).thenReturn("nuovo-hash");
        when(utenteRepository.save(any(Utente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateUtente(1L, modifica("mia@test.it", "mio", "nuova-password"));

        assertThat(esistente.getPassword()).isEqualTo("nuovo-hash");
    }

    @Test
    void updateFallsBackToExistingRoleWhenNoneGiven() {
        Utente esistente = utente(1L, "mia@test.it");
        esistente.setRuolo(Ruolo.ADMIN);
        UpdateUserRequest richiesta = modifica("mia@test.it", "mio", "");
        richiesta.setRuolo(null);

        when(utenteRepository.findById(1L)).thenReturn(Optional.of(esistente));
        when(utenteRepository.findByEmail("mia@test.it")).thenReturn(esistente);
        when(utenteRepository.findByUsername("mio")).thenReturn(esistente);
        when(utenteRepository.save(any(Utente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateUtente(1L, richiesta);

        assertThat(esistente.getRuolo()).isEqualTo(Ruolo.ADMIN);
    }
}
