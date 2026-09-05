package com.prenotazioni.booking.security;

import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.booking.service.BookingService;
import org.springframework.stereotype.Component;

/**
 * Usato da @PreAuthorize("@prenotazioneAuth.isOwnerOrAdmin(#id, principal)") sugli endpoint
 * di lettura di PrenotazioneController, al posto del controllo imperativo isOwnerOrAdmin
 * che stava prima nel controller.
 */
@Component("prenotazioneAuth")
public class BookingAuthorizationService {

    private final BookingService prenotazioneService;

    public BookingAuthorizationService(BookingService prenotazioneService) {
        this.prenotazioneService = prenotazioneService;
    }

    public boolean isOwnerOrAdmin(Long prenotazioneId, AppPrincipal principal) {
        // false qui non e' un errore mascherato: questo metodo E' un predicato, e
        // rispondere "no" a "puoi agire?" e' esattamente il suo lavoro.
        if (principal == null) {
            return false;
        }
        Booking prenotazione = prenotazioneService.getPrenotazioneById(prenotazioneId);
        // Se la prenotazione non esiste, non blocchiamo qui: lasciamo che sia il controller
        // a rispondere 404 (comportamento gia' esistente), invece di mascherarlo con un 403.
        if (prenotazione == null) {
            return true;
        }
        return prenotazione.getUtente().getId().equals(principal.id()) || principal.isAdmin();
    }
}
