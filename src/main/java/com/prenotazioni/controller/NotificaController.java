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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifiche")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:5174", "http://127.0.0.1:5174"})
public class NotificaController {

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
        System.out.println("=== NOTIFICHE COUNT ENDPOINT CHIAMATO ===");
        try {
            if (authentication == null) {
                System.out.println("ERROR: Authentication è null");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            String email = authentication.getName();
            System.out.println("Email dal token: " + email);
            
            // Cerca per email invece che per username
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                System.out.println("ERROR: Utente non trovato per email: " + email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            
            System.out.println("Utente trovato ID: " + utente.getId());

            Long count = notificaService.getCountNotificheNonLette(utente.getId());
            System.out.println("Count notifiche non lette: " + count);
            
            Map<String, Long> response = new HashMap<>();
            response.put("count", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("ERROR in getCountNotificheNonLette: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}/mark-read")
    public ResponseEntity<Notifica> markAsRead(@PathVariable Long id, Authentication authentication) {
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Optional<Notifica> notificaOpt = notificaService.getNotificaById(id);
            if (notificaOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Notifica notifica = notificaOpt.get();
            // Verifica che la notifica appartenga all'utente autenticato
            if (!notifica.getUtente().getId().equals(utente.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Notifica updatedNotifica = notificaService.markAsRead(id);
            return ResponseEntity.ok(updatedNotifica);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Map<String, String>> markAllAsRead(Authentication authentication) {
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            notificaService.markAllAsRead(utente.getId());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Tutte le notifiche sono state segnate come lette");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteNotifica(@PathVariable Long id, Authentication authentication) {
        try {
            String email = authentication.getName();
            Utente utente = utenteService.findByEmail(email);
            if (utente == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Optional<Notifica> notificaOpt = notificaService.getNotificaById(id);
            if (notificaOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Notifica notifica = notificaOpt.get();
            // Verifica che la notifica appartenga all'utente autenticato
            if (!notifica.getUtente().getId().equals(utente.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            notificaService.deleteNotifica(id);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Notifica eliminata con successo");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
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