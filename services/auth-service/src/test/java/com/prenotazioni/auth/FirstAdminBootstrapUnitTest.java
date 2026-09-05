package com.prenotazioni.auth;

import com.prenotazioni.auth.dto.CreateUserRequest;
import com.prenotazioni.auth.model.Utente;
import com.prenotazioni.auth.repository.IUtenteRepository;
import com.prenotazioni.auth.service.AuthService;
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
 * Il runner che crea il primo amministratore.
 *
 * E' codice che fabbrica un utente con privilegi massimi, quindi la garanzia che lo rende
 * accettabile - agisce ESCLUSIVAMENTE a tabella vuota - non basta scriverla in un commento:
 * va tenuta ferma da un test che fallirebbe se qualcuno la allentasse. Il primo caso qui
 * sotto e' quello, e vale piu' degli altri messi insieme.
 */
@ExtendWith(MockitoExtension.class)
class FirstAdminBootstrapUnitTest {

    @Mock
    private IUtenteRepository utenteRepository;

    @Mock
    private AuthService authService;

    private FirstAdminBootstrap runner(String email, String password) {
        return new FirstAdminBootstrap(utenteRepository, authService, email, password, "Amministratore");
    }

    @Test
    void nonToccaNienteSeCiSonoGiaDegliUtenti() {
        // LA garanzia. Se questo test cadesse, la classe smetterebbe di essere un aiuto
        // all'avvio e diventerebbe una scorciatoia per ottenere privilegi da amministratore
        // su un sistema in uso. Le credenziali passate qui sono deliberatamente valide:
        // il punto e' che non vengano usate comunque.
        when(utenteRepository.count()).thenReturn(7L);

        runner("nuovo@admin.it", "unaPasswordValida1!").run(null);

        verifyNoInteractions(authService);
    }

    @Test
    void creaLAmministratoreSuUnDatabaseVuoto() {
        when(utenteRepository.count()).thenReturn(0L);
        Utente creato = new Utente();
        creato.setId(1L);
        when(authService.register(any())).thenReturn(creato);

        runner("primo@admin.it", "unaPasswordValida1!").run(null);

        ArgumentCaptor<CreateUserRequest> richiesta = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(authService).register(richiesta.capture());
        assertThat(richiesta.getValue().getEmail()).isEqualTo("primo@admin.it");
        // Il ruolo e' minuscolo: e' il valore dell'enum Ruolo, non il nome della costante.
        // Sbagliarlo creerebbe un utente normale e il nodo resterebbe stretto, senza errori.
        assertThat(richiesta.getValue().getRuolo()).isEqualTo("admin");
    }

    @Test
    void nonFaNienteSenzaCredenziali() {
        // Il caso normale per chi non usa il meccanismo: database vuoto, variabili non
        // valorizzate. Deve essere un non-evento, non un avvio fallito.
        when(utenteRepository.count()).thenReturn(0L);

        runner("", "").run(null);

        verify(authService, never()).register(any());
    }

    @Test
    void unaSolaDelleDueNonBasta() {
        // Mezza configurazione e' piu' probabile di nessuna configurazione - si valorizza
        // l'email e ci si dimentica la password - e non deve produrre un amministratore
        // con una password vuota.
        when(utenteRepository.count()).thenReturn(0L);

        runner("primo@admin.it", "").run(null);
        runner("", "unaPasswordValida1!").run(null);

        verify(authService, never()).register(any());
    }
}
