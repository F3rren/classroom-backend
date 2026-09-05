package com.prenotazioni.notification.controller;

import com.prenotazioni.dto.MessageResponse;
import com.prenotazioni.notification.dto.BookingCancellationRequest;
import com.prenotazioni.notification.model.Notification;
import com.prenotazioni.notification.service.NotificationService;
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
public class InternalNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(InternalNotificationController.class);

    private final NotificationService notificationService;

    InternalNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // L'endpoint POST /cancellazione-prenotazione non esiste piu': quella notifica arriva
    // ora come messaggio sulla coda, gestito da CancellationListener. Una chiamata REST
    // andava persa se questo servizio era spento, un messaggio in coda lo aspetta.
    //
    // Questo endpoint di cancellazione dati resta invece sincrono, ed e' voluto: chi lo
    // chiama (auth-service) deve sapere se e' riuscito, perche' se fallisce non cancella
    // l'utente. Con una coda quella garanzia si perderebbe.

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
    public ResponseEntity<MessageResponse> deleteUserNotifications(@PathVariable("utenteId") Long userId) {
        logger.info("Eliminazione di tutte le notifiche dell'utenteId={}", userId);
        notificationService.deleteAllByUser(userId);
        return ResponseEntity.ok(new MessageResponse("Notifiche dell'utente eliminate"));
    }
}
