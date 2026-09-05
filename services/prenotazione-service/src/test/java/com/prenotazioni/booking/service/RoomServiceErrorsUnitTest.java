package com.prenotazioni.booking.service;

import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.booking.dto.RoomRequest;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.repository.RoomRepository;
import com.prenotazioni.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cosa succede quando il database rifiuta un'operazione su un'aula.
 *
 * Serviva un test proprio perche' prima non succedeva nulla di visibile: AulaService
 * catturava ogni eccezione e restituiva null, e il controller la presentava come
 * "verifica che il nome non sia gia' esistente" con un 400. Un problema di database
 * arrivava al client travestito da errore dell'utente.
 *
 * Il caso concreto non e' ipotetico: aule.nome e' UNIQUE a database, quindi due creazioni
 * concorrenti con lo stesso nome superano entrambe il controllo existsByNomeIgnoreCase e
 * una delle due viene respinta dal vincolo. Quella e' una collisione, cioe' un 409, e
 * GlobalExceptionHandler sa gia' produrlo - bastava lasciargliela arrivare.
 */
class RoomServiceErrorsUnitTest {

    private RoomRepository roomRepository;
    private RoomService service;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        service = new RoomService(roomRepository, mock(BookingRepository.class));
    }

    private RoomRequest request(String nome) {
        RoomRequest r = new RoomRequest();
        r.setNome(nome);
        r.setCapienza(30);
        r.setPiano(1);
        r.setVirtual(false);
        return r;
    }

    @Test
    void unaViolazioneDiVincoloInCreazioneNonVieneNascosta() {
        when(roomRepository.existsByNomeIgnoreCase(anyString())).thenReturn(false);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("aule_nome_key"));

        // Deve salire, non diventare null: GlobalExceptionHandler la traduce in 409.
        assertThatThrownBy(() -> service.createRoom(request("Aula Magna")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unGuastoDelDatabaseInCreazioneNonDiventaUnErroreDellUtente() {
        when(roomRepository.existsByNomeIgnoreCase(anyString())).thenReturn(false);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new IllegalStateException("connessione persa"));

        // Prima diventava un 400 "verifica che il nome non sia gia' esistente": un
        // messaggio falso, che mandava chi legge a cercare il problema nel posto sbagliato.
        assertThatThrownBy(() -> service.createRoom(request("Aula Nuova")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unNomeGiaUsatoDiventaUnConflittoDiDominio() {
        // Prima era un null, che il controller presentava come 400. Ora e' un tipo, e
        // il gestore globale lo traduce in 409 con un codice che nomina la causa.
        when(roomRepository.existsByNomeIgnoreCase("Aula Magna")).thenReturn(true);

        assertThatThrownBy(() -> service.createRoom(request("Aula Magna")))
                .isInstanceOf(DomainConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ROOM_NAME_TAKEN");
    }

    @Test
    void unaViolazioneDiVincoloInAggiornamentoNonVieneNascosta() {
        Room esistente = new Room();
        esistente.setId(1L);
        esistente.setNome("Aula A");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(esistente));
        when(roomRepository.existsByNomeIgnoreCaseAndIdNot(anyString(), anyLong())).thenReturn(false);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("aule_nome_key"));

        assertThatThrownBy(() -> service.updateRoom(1L, request("Aula B")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaCancellazioneImpeditaDaUnVincoloNonSiTravesteDaAulaInesistente() {
        Room esistente = new Room();
        esistente.setId(1L);
        when(roomRepository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("prenotazioni_aula_id_fkey"))
                .when(roomRepository).deleteById(1L);

        // Prima tornava false, indistinguibile da "aula non trovata": il client riceveva
        // un 404 su un'aula che esiste eccome, e il vero motivo - ci sono prenotazioni
        // che la referenziano - non arrivava da nessuna parte.
        assertThatThrownBy(() -> service.deleteRoom(1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancellareUnAulaInesistenteDiceCheNonEsiste() {
        // Prima tornava false, indistinguibile da "cancellazione fallita". Ora e' un 404
        // che nomina la risorsa, e "cancellazione fallita" e' un caso diverso e separato.
        when(roomRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteRoom(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ROOM_NOT_FOUND");
    }
}
