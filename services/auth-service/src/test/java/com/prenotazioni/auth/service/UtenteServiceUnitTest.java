package com.prenotazioni.auth.service;

import com.prenotazioni.auth.client.DatiUtenteClient;
import com.prenotazioni.auth.repository.IUtenteRepository;
import com.prenotazioni.exception.ServizioNonDisponibileException;
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
class UtenteServiceUnitTest {

    @Mock
    private IUtenteRepository utenteRepository;

    @Mock
    private DatiUtenteClient datiUtenteClient;

    private UtenteService service() {
        return new UtenteService(utenteRepository, datiUtenteClient);
    }

    @Test
    void cancellaLUtenteQuandoLaCascataERiuscita() {
        when(datiUtenteClient.eliminaDatiDi(7L)).thenReturn(List.of());

        service().deleteById(7L);

        verify(utenteRepository).deleteById(7L);
    }

    @Test
    void nonCancellaLUtenteSeQualcosaERimastoIndietro() {
        // L'invariante. Se cadesse, un fallimento a valle lascerebbe prenotazioni e notifiche
        // senza un utente a cui ricondurle, e ripetere l'operazione non servirebbe piu' a
        // niente: non ci sarebbe nessuno da cui ripartire.
        when(datiUtenteClient.eliminaDatiDi(7L)).thenReturn(List.of("prenotazioni"));

        assertThatThrownBy(() -> service().deleteById(7L))
                .isInstanceOf(ServizioNonDisponibileException.class);

        verify(utenteRepository, never()).deleteById(anyLong());
    }

    @Test
    void lErroreDiceCosaERimastoIndietro() {
        // "Qualcosa e' fallito" non basta a chi deve decidere se ripetere: il messaggio deve
        // nominare i dati rimasti, altrimenti l'unico modo di saperlo e' leggere i log di
        // tre servizi diversi.
        when(datiUtenteClient.eliminaDatiDi(7L)).thenReturn(List.of("notifiche", "prenotazioni"));

        assertThatThrownBy(() -> service().deleteById(7L))
                .isInstanceOf(ServizioNonDisponibileException.class)
                .hasMessageContaining("notifiche")
                .hasMessageContaining("prenotazioni");
    }

    @Test
    void lErroreInvitaARipetere() {
        // Il messaggio per l'utente e il codice sono cio' che distingue "e' rotto" da
        // "riprova". Sono due azioni diverse, e con un 500 generico la seconda non veniva
        // in mente: l'operazione restava a meta' perche' nessuno la ripeteva.
        when(datiUtenteClient.eliminaDatiDi(7L)).thenReturn(List.of("notifiche"));

        ServizioNonDisponibileException errore = (ServizioNonDisponibileException)
                org.assertj.core.api.Assertions.catchThrowable(() -> service().deleteById(7L));

        assertThat(errore.getErrorCode()).isEqualTo("USER_DELETE_INCOMPLETE");
        assertThat(errore.getUserMessage()).containsIgnoringCase("riprova");
    }

    @Test
    void laCascataVienePrimaDellaCancellazione() {
        // L'ordine, non solo l'esito: i dati a valle si tentano SEMPRE, anche quando poi
        // andra' tutto bene. Se un giorno qualcuno invertisse le due righe, gli altri test
        // continuerebbero a passare mentre l'invariante sarebbe gia' persa.
        when(datiUtenteClient.eliminaDatiDi(7L)).thenReturn(List.of());

        service().deleteById(7L);

        var ordine = org.mockito.Mockito.inOrder(datiUtenteClient, utenteRepository);
        ordine.verify(datiUtenteClient).eliminaDatiDi(7L);
        ordine.verify(utenteRepository).deleteById(7L);
    }
}
