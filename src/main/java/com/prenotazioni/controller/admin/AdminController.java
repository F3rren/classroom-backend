package com.prenotazioni.controller.admin;

import com.prenotazioni.service.AuthService;
import com.prenotazioni.service.JwtService;
import com.prenotazioni.service.AulaService;
import com.prenotazioni.service.PrenotazioneService;
import com.prenotazioni.dto.RegisterRequest;
import com.prenotazioni.dto.AulaRequest;
import com.prenotazioni.model.Utente;
import com.prenotazioni.model.Aula;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AuthService authService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AulaService aulaService;
    @Autowired
    private PrenotazioneService prenotazioneService;

    // Metodo privato per verificare se l'utente è admin
    private ResponseEntity<?> checkAdminAccess(String authHeader) {
        logger.debug("INIZIO checkAdminAccess");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.debug("FINE checkAdminAccess - Token di autorizzazione mancante");
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Token di autorizzazione mancante"),
                HttpStatus.UNAUTHORIZED
            );
        }

        logger.debug("Token di autorizzazione presente");
        String token = authHeader.substring(7);
        if (!jwtService.validateToken(token)) {
            logger.debug("FINE checkAdminAccess - Token non valido");
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Token non valido"),
                HttpStatus.UNAUTHORIZED
            );
        }

        String ruolo = jwtService.getRuoloFromToken(token);
        if (!"admin".equals(ruolo)) {
            logger.debug("FINE checkAdminAccess - Accesso negato - Ruolo: {}", ruolo);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Accesso negato: solo gli amministratori possono accedere"),
                HttpStatus.FORBIDDEN
            );
        }

        logger.debug("FINE checkAdminAccess - Accesso admin consentito");
        return null; // Access granted
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO register - Email: {}, Username: {}, Ruolo: {}", request.getEmail(), request.getUsername(), request.getRuolo());
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE register - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, tentativo di registrazione utente");
        try {
            Utente utente = authService.register(request);
            if (utente == null) {
                logger.debug("FINE register - Registrazione fallita - Email o username già esistenti");
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Email o username già esistenti"),
                    HttpStatus.BAD_REQUEST
                );
            }
            
            logger.debug("FINE register - Utente registrato con successo - ID: {}, Email: {}", utente.getId(), utente.getEmail());
            return new ResponseEntity<>(
                Collections.singletonMap("success", "Utente registrato con successo dall'amministratore"),
                HttpStatus.CREATED
            );
        } catch (Exception e) {
            logger.error("FINE register - Errore interno durante registrazione: {}", e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Lista tutti gli utenti
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getAllUsers");
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE getAllUsers - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, recupero tutti gli utenti");
        try {
            List<Utente> users = authService.getAllUsers();
            if (users == null || users.isEmpty()) {
                logger.debug("FINE getAllUsers - Nessun utente trovato");
                return new ResponseEntity<>(
                    Collections.singletonMap("message", "Nessun utente trovato"),
                    HttpStatus.OK
                );
            }

            logger.debug("FINE getAllUsers - Utenti recuperati con successo, totale: {}", users.size());
            return new ResponseEntity<>(
                Collections.singletonMap("users", users),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE getAllUsers - Errore interno: {}", e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Modifica utente
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUtente(@PathVariable Long id, @RequestBody RegisterRequest request, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO updateUtente - ID Utente: {}, Nuova Email: {}", id, request.getEmail());
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE updateUtente - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, tentativo di modifica utente ID: {}", id);
        try {
            Utente updated = authService.updateUtente(id, request);
            if (updated == null) {
                logger.debug("FINE updateUtente - Utente non trovato o non modificabile - ID: {}", id);
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Utente non trovato o non modificabile"),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.debug("FINE updateUtente - Utente modificato con successo - ID: {}, Email: {}", updated.getId(), updated.getEmail());
            return new ResponseEntity<>(
                Collections.singletonMap("message", "Utente selezionato modificato con successo"),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE updateUtente - Errore interno per ID utente: {}, Errore: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Eliminazione utente
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteUtente(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO deleteUtente - ID Utente: {}", id);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE deleteUtente - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, tentativo di eliminazione utente ID: {}", id);
        try {
            boolean deleted = authService.deleteUtente(id);
            if (!deleted) {
                logger.debug("FINE deleteUtente - Utente non trovato o non eliminabile - ID: {}", id);
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Utente non trovato o non eliminabile"),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.debug("FINE deleteUtente - Utente eliminato con successo - ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("message", "Utente eliminato con successo"),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE deleteUtente - Errore interno per ID utente: {}, Errore: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Lista tutte le aule
    @GetMapping("/rooms")
    public ResponseEntity<?> getAllRooms(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getAllRooms (admin)");
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE getAllRooms - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, recupero tutte le aule");
        try {
            List<Aula> aule = aulaService.getAllAule();
            if (aule == null || aule.isEmpty()) {
                logger.debug("FINE getAllRooms - Nessuna aula trovata");
                return new ResponseEntity<>(
                    Collections.singletonMap("message", "Nessuna aula trovata"),
                    HttpStatus.OK
                );
            }

            logger.debug("FINE getAllRooms - Aule recuperate con successo, totale: {}", aule.size());
            return new ResponseEntity<>(
                Collections.singletonMap("rooms", aule),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE getAllRooms - Errore interno: {}", e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Ottieni singola aula per ID
    @GetMapping("/rooms/{id}")
    public ResponseEntity<?> getRoomById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getRoomById (admin) - ID Aula: {}", id);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE getRoomById - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, recupero aula con ID: {}", id);
        try {
            java.util.Optional<Aula> aula = aulaService.getAulaById(id);
            if (aula.isEmpty()) {
                logger.debug("FINE getRoomById - Aula non trovata con ID: {}", id);
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Aula non trovata"),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.debug("FINE getRoomById - Aula recuperata con successo: ID: {}, Nome: {}", aula.get().getId(), aula.get().getNome());
            return new ResponseEntity<>(
                Collections.singletonMap("room", aula.get()),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE getRoomById - Errore interno per ID aula: {}, Errore: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Gestione stanze - Creazione stanza
    @PostMapping("/createrooms")
    public ResponseEntity<?> createRoom(@RequestBody AulaRequest roomRequest, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO createRoom - Nome: {}, Piano: {}, Capienza: {}", roomRequest.getNome(), roomRequest.getPiano(), roomRequest.getCapienza());
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE createRoom - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, tentativo di creazione aula");
        try {
            Aula nuovaAula = aulaService.createAula(roomRequest);
            if (nuovaAula == null) {
                logger.debug("FINE createRoom - Impossibile creare aula - Nome: {}", roomRequest.getNome());
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Impossibile creare l'aula. Verifica che il nome non sia già esistente e che i dati siano validi."),
                    HttpStatus.BAD_REQUEST
                );
            }

            logger.debug("FINE createRoom - Aula creata con successo - ID: {}, Nome: {}", nuovaAula.getId(), nuovaAula.getNome());
            return new ResponseEntity<>(
                Map.of("message", "Aula creata con successo", "aulaId", nuovaAula.getId()),
                HttpStatus.CREATED
            );
        } catch (Exception e) {
            logger.error("FINE createRoom - Errore interno durante creazione aula: {}", e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Modifica stanza
    @PutMapping("/rooms/{id}")
    public ResponseEntity<?> updateRoom(@PathVariable Long id, @RequestBody AulaRequest roomRequest, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO updateRoom - ID Aula: {}, Nuovo Nome: {}", id, roomRequest.getNome());
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE updateRoom - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, tentativo di aggiornamento aula ID: {}", id);
        try {
            Aula aulaAggiornata = aulaService.updateAula(id, roomRequest);
            if (aulaAggiornata == null) {
                logger.debug("FINE updateRoom - Impossibile aggiornare aula ID: {}", id);
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Impossibile aggiornare l'aula. Verifica che l'ID sia corretto e che i dati siano validi."),
                    HttpStatus.BAD_REQUEST
                );
            }

            logger.debug("FINE updateRoom - Aula aggiornata con successo - ID: {}, Nome: {}", aulaAggiornata.getId(), aulaAggiornata.getNome());
            return new ResponseEntity<>(
                Map.of("message", "Aula aggiornata con successo", "aula", aulaAggiornata),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE updateRoom - Errore interno per ID aula: {}, Errore: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Eliminazione stanza
    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO deleteRoom - ID Aula: {}", id);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE deleteRoom - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, tentativo di eliminazione aula ID: {}", id);
        try {
            boolean eliminata = aulaService.deleteAula(id);
            if (!eliminata) {
                logger.debug("FINE deleteRoom - Impossibile eliminare aula ID: {}", id);
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Impossibile eliminare l'aula. Verifica che l'ID sia corretto."),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.debug("FINE deleteRoom - Aula eliminata con successo - ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("message", "Aula eliminata con successo"),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE deleteRoom - Errore interno per ID aula: {}, Errore: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // ========== GESTIONE PRENOTAZIONI ADMIN ==========

    // Elimina prenotazione come admin (può eliminare qualsiasi prenotazione)
    @DeleteMapping("/prenotazioni/{id}")
    public ResponseEntity<?> deletePrenotazioneAsAdmin(@PathVariable Long id, 
                                                      @RequestHeader("Authorization") String authHeader,
                                                      @RequestBody(required = false) Map<String, String> requestBody) {
        logger.debug("INIZIO deletePrenotazioneAsAdmin - ID Prenotazione: {}", id);
        
        ResponseEntity<?> accessCheck = checkAdminAccess(authHeader);
        if (accessCheck != null) {
            logger.debug("FINE deletePrenotazioneAsAdmin - Accesso admin negato");
            return accessCheck;
        }

        logger.debug("Accesso admin confermato, tentativo di eliminazione prenotazione ID: {}", id);
        try {
            // Estrai il token per ottenere l'ID dell'admin
            String token = authHeader.substring(7);
            Long adminId = jwtService.getUserIdFromToken(token);
            logger.debug("Admin ID: {} tenta di eliminare prenotazione: {}", adminId, id);
            
            // Motivo opzionale per l'eliminazione
            String motivo = (requestBody != null && requestBody.get("reason") != null) 
                ? requestBody.get("reason") 
                : "Eliminazione da parte dell'amministratore";
            logger.debug("Motivo eliminazione: {}", motivo);

            // Tentativo di eliminazione forzata per admin
            boolean eliminata = prenotazioneService.annullaPrenotazioneAsAdmin(id, adminId, motivo);
            
            if (!eliminata) {
                logger.debug("FINE deletePrenotazioneAsAdmin - Impossibile eliminare prenotazione ID: {}", id);
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Impossibile eliminare la prenotazione. Verifica che l'ID sia corretto."),
                    HttpStatus.NOT_FOUND
                );
            }

            logger.debug("FINE deletePrenotazioneAsAdmin - Prenotazione eliminata con successo - ID: {}, Admin: {}", id, adminId);
            return new ResponseEntity<>(
                Map.of(
                    "message", "Prenotazione eliminata con successo dall'amministratore",
                    "adminAction", true,
                    "reason", motivo
                ),
                HttpStatus.OK
            );

        } catch (Exception e) {
            logger.error("FINE deletePrenotazioneAsAdmin - Errore durante eliminazione prenotazione ID: {}, Errore: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore durante l'eliminazione della prenotazione: " + e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
