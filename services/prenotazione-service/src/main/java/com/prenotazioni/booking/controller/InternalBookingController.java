package com.prenotazioni.booking.controller;

import com.prenotazioni.dto.MessageResponse;
import com.prenotazioni.booking.repository.BookingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint chiamati da altri servizi, non dal frontend.
 *
 * Esiste per una ragione sola: quando auth-service cancella un utente deve poter
 * rimuovere le sue prenotazioni, che vivono in questo database. Finche' tutto stava
 * insieme lo faceva una chiave esterna con ON DELETE, e prima ancora una singola
 * transazione. Ora e' una chiamata di rete, e puo' fallire.
 *
 * Il gateway chiude /api/prenotazioni/interne/** dall'esterno: raggiungerlo richiede di
 * parlare direttamente con questo servizio. La protezione non si ferma pero' li',
 * perche' un gateway aggirato non deve bastare: serve comunque un token con ruolo ADMIN,
 * verificato qui come su qualunque altro endpoint.
 */
@RestController
@RequestMapping("/api/prenotazioni/interne")
@Tag(name = "Prenotazioni (interne)", description = "Chiamate da altri servizi, non dal frontend")
@PreAuthorize("hasRole('ADMIN')")
public class InternalBookingController {

    private static final Logger logger = LoggerFactory.getLogger(InternalBookingController.class);

    private final BookingRepository prenotazioneRepository;

    InternalBookingController(BookingRepository prenotazioneRepository) {
        this.prenotazioneRepository = prenotazioneRepository;
    }

    @DeleteMapping("/utente/{utenteId}")
    @Operation(summary = "Elimina le prenotazioni di un utente che sta per essere rimosso")
    @Transactional
    public ResponseEntity<MessageResponse> eliminaPrenotazioniUtente(@PathVariable Long utenteId) {
        logger.info("Eliminazione prenotazioni dell'utenteId={} su richiesta del servizio utenti", utenteId);
        prenotazioneRepository.deleteByUtenteId(utenteId);
        return ResponseEntity.ok(new MessageResponse("Prenotazioni dell'utente eliminate"));
    }
}
