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
 * La cancellazione di un utente e dei suoi dati negli altri servizi.
 *
 * Cancellare un utente deve portarsi via anche le sue prenotazioni e le sue notifiche, che
 * pero' vivono in due altri database dietro due altri servizi: non c'e' una transazione che
 * copra tutti e tre, e ogni pezzo puo' fallire per conto suo.
 *
 * L'invariante che rende accettabile questa cosa e' UNA, ed e' quella che i test qui sotto
 * tengono ferma: l'utente si cancella per ULTIMO. Finche' c'e' lui, le righe rimaste altrove
 * hanno ancora un proprietario a cui essere ricondotte e l'operazione si puo' ripetere.
 * Cancellarlo per primo lascerebbe dati di cui nessuno sa piu' di chi siano - e nessuna
 * ripetizione potrebbe rimediare.
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
    void cancellaLUtenteQuandoLaCascataERiuscita() {
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of());

        service().deleteById(7L);

        verify(userRepository).deleteById(7L);
    }

    @Test
    void nonCancellaLUtenteSeQualcosaERimastoIndietro() {
        // L'invariante. Se cadesse, un fallimento a valle lascerebbe prenotazioni e notifiche
        // senza un utente a cui ricondurle, e ripetere l'operazione non servirebbe piu' a
        // niente: non ci sarebbe nessuno da cui ripartire.
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of("bookings"));

        assertThatThrownBy(() -> service().deleteById(7L))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void lErroreDiceCosaERimastoIndietro() {
        // "Qualcosa e' fallito" non basta a chi deve decidere se ripetere: il messaggio deve
        // nominare i dati rimasti, altrimenti l'unico modo di saperlo e' leggere i log di
        // tre servizi diversi.
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of("notifications", "bookings"));

        assertThatThrownBy(() -> service().deleteById(7L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("notifications")
                .hasMessageContaining("bookings");
    }

    @Test
    void lErroreInvitaARipetere() {
        // Il messaggio per l'utente e il codice sono cio' che distingue "e' rotto" da
        // "riprova". Sono due azioni diverse, e con un 500 generico la seconda non veniva
        // in mente: l'operazione restava a meta' perche' nessuno la ripeteva.
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of("notifications"));

        ServiceUnavailableException error = (ServiceUnavailableException)
                org.assertj.core.api.Assertions.catchThrowable(() -> service().deleteById(7L));

        assertThat(error.getErrorCode()).isEqualTo("USER_DELETE_INCOMPLETE");
        assertThat(error.getUserMessage()).containsIgnoringCase("riprova");
    }

    @Test
    void laCascataVienePrimaDellaCancellazione() {
        // L'ordine, non solo l'esito: i dati a valle si tentano SEMPRE, anche quando poi
        // andra' tutto bene. Se un giorno qualcuno invertisse le due righe, gli altri test
        // continuerebbero a passare mentre l'invariante sarebbe gia' persa.
        when(userDataClient.deleteDataOf(7L)).thenReturn(List.of());

        service().deleteById(7L);

        var order = org.mockito.Mockito.inOrder(userDataClient, userRepository);
        order.verify(userDataClient).deleteDataOf(7L);
        order.verify(userRepository).deleteById(7L);
    }
}
