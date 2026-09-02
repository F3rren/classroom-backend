package com.prenotazioni.notifica.controller;

import com.prenotazioni.dto.MessageResponse;
import com.prenotazioni.notifica.dto.CancellazionePrenotazioneRequest;
import com.prenotazioni.notifica.model.Notifica;
import com.prenotazioni.notifica.service.NotificaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gli endpoint che questo servizio espone ad ALTRI servizi, non al frontend.
 *
 * Prima erano chiamate a metodo dentro lo stesso processo: AdminController invocava
 * NotificaService dopo aver cancellato una prenotazione, e UtenteService cancellava le
 * notifiche di un utente eliminato. Adesso sono confini di rete.
 *
 * Non c'e' un meccanismo di autenticazione nuovo: il chiamante inoltra il token JWT
 * dell'amministratore che ha avviato l'operazione, e questo servizio lo verifica da solo
 * come qualunque altra richiesta. Entrambe le operazioni sono per definizione azioni
 * amministrative, quindi hasRole('ADMIN') e' il controllo corretto e non un ripiego.
 */
@RestController
@RequestMapping("/api/notifiche/interne")
@Tag(name = "Notifiche (interne)", description = "Chiamate da altri servizi, non dal frontend")
public class NotificaInternaController {

    private static final Logger logger = LoggerFactory.getLogger(NotificaInternaController.class);

    private final NotificaService notificaService;

    NotificaInternaController(NotificaService notificaService) {
        this.notificaService = notificaService;
    }

    @PostMapping("/cancellazione-prenotazione")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crea la notifica di una prenotazione cancellata da un admin")
    public ResponseEntity<Notifica> notificaCancellazione(@Valid @RequestBody CancellazionePrenotazioneRequest richiesta) {
        logger.debug("Notifica di cancellazione richiesta per utenteId={}, prenotazioneId={}",
                richiesta.getUtenteId(), richiesta.getPrenotazioneId());

        Notifica notifica = notificaService.createNotificaCancellazionePrenotazione(
                richiesta.getUtenteId(),
                richiesta.getPrenotazioneId(),
                richiesta.getNomeStanza(),
                richiesta.getAdminNome(),
                richiesta.getDataPrenotazione(),
                richiesta.getOraInizio(),
                richiesta.getOraFine(),
                richiesta.getMotivo());

        return ResponseEntity.ok(notifica);
    }

    /**
     * Cancella tutte le notifiche di un utente eliminato.
     *
     * Prima faceva parte della stessa transazione che eliminava utente e prenotazioni;
     * ora e' una chiamata separata, quindi l'operazione complessiva non e' piu' atomica.
     * Se questa fallisce restano notifiche orfane: e' il costo della separazione, e verra'
     * affrontato con un evento quando ci sara' un broker.
     */
    @DeleteMapping("/utente/{utenteId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Elimina tutte le notifiche di un utente")
    public ResponseEntity<MessageResponse> eliminaNotificheUtente(@PathVariable Long utenteId) {
        logger.info("Eliminazione di tutte le notifiche dell'utenteId={}", utenteId);
        notificaService.deleteAllByUtente(utenteId);
        return ResponseEntity.ok(new MessageResponse("Notifiche dell'utente eliminate"));
    }
}
