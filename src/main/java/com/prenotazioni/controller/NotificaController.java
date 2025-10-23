package com.prenotazioni.controller;

import com.prenotazioni.model.Notifica;
import com.prenotazioni.model.Utente;
import com.prenotazioni.service.NotificaService;
import com.prenotazioni.service.UtenteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifiche")
public class NotificaController {

    private static final Logger logger = LoggerFactory.getLogger(NotificaController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private NotificaService notificaService;

    @Autowired
    private UtenteService utenteService;

    @GetMapping
    public ResponseEntity<List<Notifica>> getNotifiche(Authentication authentication) {
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            List<Notifica> notifiche = notificaService.getNotificheByUtente(utente.getId());
            return ResponseEntity.ok(notifiche);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/non-lette")
    public ResponseEntity<List<Notifica>> getNotificheNonLette(Authentication authentication) {
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            List<Notifica> notifiche = notificaService.getNotificheNonLetteByUtente(utente.getId());
            return ResponseEntity.ok(notifiche);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/count-non-lette")
    public ResponseEntity<Map<String, Long>> getCountNotificheNonLette(Authentication authentication) {
        logger.info("NOTIFICHE COUNT ENDPOINT CHIAMATO");
        try {
            if (authentication == null) {
                logger.error("ERROR: Authentication è null");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            String email = authentication.getName();
            logger.info("Email dal token: " + email);
            
            // Cerca per email invece che per username
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                logger.error("ERROR: Utente non trovato per email: " + email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            
            logger.info("Utente trovato ID: " + utente.getId());

            Long count = notificaService.getCountNotificheNonLette(utente.getId());
            logger.info("Count notifiche non lette: " + count);
            
            Map<String, Long> response = new HashMap<>();
            response.put("count", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("ERROR in getCountNotificheNonLette: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}/mark-read")
    public ResponseEntity<Notifica> markAsRead(@PathVariable Long id, Authentication authentication) {
        logger.info("INIZIO - Richiesta di segnare notifica come letta, ID: {}", id);
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                logger.warn("Utente non trovato per email: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Optional<Notifica> updatedNotificaOpt = notificaService.markAsRead(id, utente.getId());

            if (updatedNotificaOpt.isPresent()) {
                logger.info("FINE - Notifica ID: {} segnata come letta con successo.", id);
                return ResponseEntity.ok(updatedNotificaOpt.get());
            } else {
                // Il service ha già loggato il motivo (notifica non trovata o non autorizzato)
                logger.warn("FINE - Impossibile segnare notifica ID: {} come letta. Potrebbe non esistere o l'utente non è autorizzato.", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // O FORBIDDEN, ma NOT_FOUND è più sicuro
            }
        } catch (Exception e) {
            logger.error("ERRORE non gestito in markAsRead per notifica ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Map<String, String>> markAllAsRead(Authentication authentication) {
        logger.info("INIZIO - Richiesta di segnare tutte le notifiche come lette.");
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                logger.warn("Utente non trovato per email: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            notificaService.markAllAsRead(utente.getId());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Tutte le notifiche sono state segnate come lette");
            logger.info("FINE - Tutte le notifiche per l'utente {} sono state segnate come lette.", email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("ERRORE non gestito in markAllAsRead", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteNotifica(@PathVariable Long id, Authentication authentication) {
        logger.info("INIZIO - Richiesta di eliminazione notifica ID: {}", id);
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                logger.warn("Utente non trovato per email: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Optional<Notifica> notificaOpt = notificaService.getNotificaById(id);
            if (notificaOpt.isEmpty()) {
                logger.warn("Notifica da eliminare non trovata, ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Notifica notifica = notificaOpt.get();
            // Verifica che la notifica appartenga all'utente autenticato
            if (!notifica.getUtente().getId().equals(utente.getId())) {
                logger.warn("Utente {} non autorizzato a eliminare notifica ID: {}", email, id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            notificaService.deleteNotifica(id);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Notifica eliminata con successo");
            logger.info("FINE - Notifica ID: {} eliminata con successo.", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("ERRORE non gestito in deleteNotifica per ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/read")
    public ResponseEntity<Map<String, String>> deleteReadNotifications(Authentication authentication) {
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            notificaService.deleteReadNotifications(utente.getId());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Notifiche lette eliminate con successo");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}