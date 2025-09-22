package com.prenotazioni.controller;

import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.service.PrenotazioneService;
import com.prenotazioni.service.JwtService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prenotazioni")
public class PrenotazioneController {
    
    private static final Logger logger = LoggerFactory.getLogger(PrenotazioneController.class);
    
    @Autowired
    private PrenotazioneService prenotazioneService;
    
    @Autowired
    private JwtService jwtService;
    
    // DTO per le richieste di prenotazione
    public static class PrenotazioneRequest {
        private Long aulaId;
        private Long corsoId;
        private String inizio; // formato: "2024-12-25T14:30:00"
        private String fine;
        private String descrizione;
        
        // Getters e Setters
        public Long getAulaId() { return aulaId; }
        public void setAulaId(Long aulaId) { this.aulaId = aulaId; }
        public Long getCorsoId() { return corsoId; }
        public void setCorsoId(Long corsoId) { this.corsoId = corsoId; }
        public String getInizio() { return inizio; }
        public void setInizio(String inizio) { this.inizio = inizio; }
        public String getFine() { return fine; }
        public void setFine(String fine) { this.fine = fine; }
        public String getDescrizione() { return descrizione; }
        public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    }
    
    // Metodo privato per verificare autenticazione (senza controllo ruolo)
    private ResponseEntity<?> checkAuth(String authHeader) {
        logger.debug("INIZIO checkAuth");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.debug("FINE checkAuth - Token di autorizzazione mancante");
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Token di autorizzazione mancante"),
                HttpStatus.UNAUTHORIZED
            );
        }
        logger.debug("Token di autorizzazione presente");
        String token = authHeader.substring(7);
        if (!jwtService.validateToken(token)) {
            logger.debug("FINE checkAuth - Token non valido");
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Token non valido"),
                HttpStatus.UNAUTHORIZED
            );
        }
        logger.debug("FINE checkAuth - Token valido");
        return null; // Access granted per tutti gli utenti autenticati
    }
    
    // Prenota un'aula
    @PostMapping("/prenota")
    public ResponseEntity<?> prenotaAula(@RequestBody PrenotazioneRequest request,
                                        @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO prenotaAula - AulaId: {}, CorsoId: {}, Inizio: {}, Fine: {}", 
                   request.getAulaId(), request.getCorsoId(), request.getInizio(), request.getFine());
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE prenotaAula - Autenticazione fallita");
            return authCheck;
        }
        
        try {
            String token = authHeader.substring(7);
            Long utenteId = jwtService.getUserIdFromToken(token);
            logger.debug("Utente autenticato con ID: {}", utenteId);
            
            LocalDateTime inizio = LocalDateTime.parse(request.getInizio());
            LocalDateTime fine = LocalDateTime.parse(request.getFine());
            
            Prenotazione prenotazione = prenotazioneService.prenotaAula(
                request.getAulaId(), request.getCorsoId(), utenteId, 
                inizio, fine, request.getDescrizione()
            );
            
            if (prenotazione == null) {
                logger.debug("FINE prenotaAula - Prenotazione fallita per aula ID: {} nel periodo {}-{}", 
                           request.getAulaId(), inizio, fine);
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Impossibile prenotare: aula non disponibile o dati non validi"),
                    HttpStatus.CONFLICT
                );
            }
            
            logger.debug("FINE prenotaAula - Prenotazione creata con successo - ID: {}, Utente: {}, Aula: {}", 
                       prenotazione.getId(), utenteId, request.getAulaId());
            return new ResponseEntity<>(
                Map.of("message", "Prenotazione effettuata con successo", "prenotazione", prenotazione),
                HttpStatus.CREATED
            );
            
        } catch (DateTimeParseException e) {
            logger.debug("FINE prenotaAula - Errore parsing data - Input: {}, Errore: {}", request.getInizio() + " - " + request.getFine(), e.getMessage());
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Formato data non valido. Usa: YYYY-MM-DDTHH:MM:SS"),
                HttpStatus.BAD_REQUEST
            );
        } catch (Exception e) {
            logger.error("FINE prenotaAula - Errore interno durante prenotazione - Utente: {}, Aula: {}, Errore: {}", 
                        jwtService.getUserIdFromToken(authHeader.substring(7)), request.getAulaId(), e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Blocca un'aula (solo admin)
    @PostMapping("/blocca")
    public ResponseEntity<?> bloccaAula(@RequestBody PrenotazioneRequest request,
                                       @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO bloccaAula - AulaId: {}, Inizio: {}, Fine: {}", 
                   request.getAulaId(), request.getInizio(), request.getFine());
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE bloccaAula - Autenticazione fallita");
            return authCheck;
        }
        
        String token = authHeader.substring(7);
        String ruolo = jwtService.getRuoloFromToken(token);
        if (!"admin".equals(ruolo)) {
            logger.debug("FINE bloccaAula - Accesso negato - Ruolo: {}", ruolo);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Solo gli amministratori possono bloccare le aule"),
                HttpStatus.FORBIDDEN
            );
        }
        
        logger.debug("Autenticazione e autorizzazione admin riuscita");
        
        try {
            Long utenteId = jwtService.getUserIdFromToken(token);
            LocalDateTime inizio = LocalDateTime.parse(request.getInizio());
            LocalDateTime fine = LocalDateTime.parse(request.getFine());
            
            Prenotazione blocco = prenotazioneService.bloccaAula(
                request.getAulaId(), utenteId, inizio, fine, request.getDescrizione()
            );
            
            if (blocco == null) {
                logger.debug("FINE bloccaAula - Impossibile bloccare aula ID: {}", request.getAulaId());
                return new ResponseEntity<>(
                    Collections.singletonMap("error", "Impossibile bloccare l'aula"),
                    HttpStatus.CONFLICT
                );
            }
            
            logger.debug("FINE bloccaAula - Aula bloccata con successo - ID blocco: {}, Aula: {}", 
                       blocco.getId(), request.getAulaId());
            return new ResponseEntity<>(
                Map.of("message", "Aula bloccata con successo", "blocco", blocco),
                HttpStatus.CREATED
            );
            
        } catch (DateTimeParseException e) {
            logger.debug("FINE bloccaAula - Errore parsing data: {}", e.getMessage());
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Formato data non valido"),
                HttpStatus.BAD_REQUEST
            );
        } catch (Exception e) {
            logger.error("FINE bloccaAula - Errore interno: {}", e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Verifica disponibilità aula
    @GetMapping("/disponibilita")
    public ResponseEntity<?> verificaDisponibilita(@RequestParam Long aulaId,
                                                   @RequestParam String inizio,
                                                   @RequestParam String fine) {
        logger.debug("INIZIO verificaDisponibilita - AulaId: {}, Periodo: {} - {}", aulaId, inizio, fine);
        
        try {
            LocalDateTime inizioDateTime = LocalDateTime.parse(inizio);
            LocalDateTime fineDateTime = LocalDateTime.parse(fine);
            
            boolean disponibile = prenotazioneService.isAulaDisponibile(aulaId, inizioDateTime, fineDateTime);
            
            logger.debug("FINE verificaDisponibilita - AulaId: {}, Disponibile: {}", aulaId, disponibile);
            return new ResponseEntity<>(
                Map.of("aulaId", aulaId, "disponibile", disponibile, 
                       "periodo", inizio + " - " + fine),
                HttpStatus.OK
            );
            
        } catch (DateTimeParseException e) {
            logger.debug("FINE verificaDisponibilita - Errore parsing data: {}", e.getMessage());
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Formato data non valido"),
                HttpStatus.BAD_REQUEST
            );
        } catch (Exception e) {
            logger.error("FINE verificaDisponibilita - Errore interno: {}", e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Stato attuale di un'aula
    @GetMapping("/stato/{aulaId}")
    public ResponseEntity<?> getStatoAula(@PathVariable Long aulaId) {
        logger.debug("INIZIO getStatoAula - AulaId: {}", aulaId);
        
        try {
            String stato = prenotazioneService.getStatoAula(aulaId, LocalDateTime.now());
            
            logger.debug("FINE getStatoAula - AulaId: {}, Stato: {}", aulaId, stato);
            return new ResponseEntity<>(
                Map.of("aulaId", aulaId, "stato", stato, "timestamp", LocalDateTime.now()),
                HttpStatus.OK
            );
        } catch (Exception e) {
            logger.error("FINE getStatoAula - Errore interno per AulaId: {}, Errore: {}", aulaId, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Lista prenotazioni utente
    @GetMapping("/mie")
    public ResponseEntity<?> getMiePrenotazioni(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getMiePrenotazioni");
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getMiePrenotazioni - Autenticazione fallita");
            return authCheck;
        }
        
        String token = authHeader.substring(7);
        Long utenteId = jwtService.getUserIdFromToken(token);
        logger.debug("Utente autenticato con ID: {}", utenteId);
        
        List<Prenotazione> prenotazioni = prenotazioneService.getPrenotazioniUtente(utenteId);
        
        logger.debug("FINE getMiePrenotazioni - Prenotazioni recuperate per utente: {}, totale: {}", utenteId, prenotazioni.size());
        return new ResponseEntity<>(
            Collections.singletonMap("prenotazioni", prenotazioni),
            HttpStatus.OK
        );
    }
    
    // Annulla prenotazione
    @DeleteMapping("/{prenotazioneId}")
    public ResponseEntity<?> annullaPrenotazione(@PathVariable Long prenotazioneId,
                                                @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO annullaPrenotazione - PrenotazioneId: {}", prenotazioneId);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE annullaPrenotazione - Autenticazione fallita");
            return authCheck;
        }
        
        String token = authHeader.substring(7);
        Long utenteId = jwtService.getUserIdFromToken(token);
        logger.debug("Tentativo di annullamento prenotazione {} da parte dell'utente {}", prenotazioneId, utenteId);
        
        boolean annullata = prenotazioneService.annullaPrenotazione(prenotazioneId, utenteId);
        
        if (!annullata) {
            logger.debug("FINE annullaPrenotazione - Impossibile annullare prenotazione ID: {} per utente: {}", prenotazioneId, utenteId);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Impossibile annullare la prenotazione"),
                HttpStatus.FORBIDDEN
            );
        }
        
        logger.debug("FINE annullaPrenotazione - Prenotazione annullata con successo - ID: {}, Utente: {}", prenotazioneId, utenteId);
        return new ResponseEntity<>(
            Collections.singletonMap("message", "Prenotazione annullata con successo"),
            HttpStatus.OK
        );
    }

    // ========== NUOVI ENDPOINT PER GESTIONE PRENOTAZIONI ==========

    // Lista tutte le prenotazioni (semplice) - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping
    public ResponseEntity<?> getAllPrenotazioni(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getAllPrenotazioni");
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getAllPrenotazioni - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero tutte le prenotazioni");
        List<Prenotazione> prenotazioni = prenotazioneService.getAllPrenotazioni();
        if (prenotazioni == null || prenotazioni.isEmpty()) {
            logger.debug("FINE getAllPrenotazioni - Nessuna prenotazione trovata");
            return new ResponseEntity<>(
                Collections.singletonMap("message", "Nessuna prenotazione trovata"),
                HttpStatus.OK
            );
        }

        logger.debug("FINE getAllPrenotazioni - Prenotazioni recuperate con successo, totale: {}", prenotazioni.size());
        return new ResponseEntity<>(
            Collections.singletonMap("prenotazioni", prenotazioni),
            HttpStatus.OK
        );
    }

    // Singola prenotazione per ID (semplice) - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/{id}")
    public ResponseEntity<?> getPrenotazioneById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getPrenotazioneById - ID Prenotazione: {}", id);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getPrenotazioneById - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero prenotazione con ID: {}", id);
        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            logger.debug("FINE getPrenotazioneById - Prenotazione non trovata con ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Prenotazione non trovata"),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("FINE getPrenotazioneById - Prenotazione recuperata con successo: ID: {}", prenotazione.getId());
        return new ResponseEntity<>(
            Collections.singletonMap("prenotazione", prenotazione),
            HttpStatus.OK
        );
    }

    // Dettagli completi di una prenotazione specifica - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/{id}/details")
    public ResponseEntity<?> getPrenotazioneDetailsById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getPrenotazioneDetailsById - ID Prenotazione: {}", id);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getPrenotazioneDetailsById - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero dettagli completi per prenotazione con ID: {}", id);
        // Prima verifica se la prenotazione esiste
        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            logger.debug("FINE getPrenotazioneDetailsById - Prenotazione non trovata con ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Prenotazione non trovata"),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("Prenotazione trovata: ID: {}", prenotazione.getId());
        // Ottieni i dettagli completi
        List<Map<String, Object>> dettagliCompleti = prenotazioneService.getPrenotazioneCompleteDetails(id);
        
        logger.debug("FINE getPrenotazioneDetailsById - Dettagli completi recuperati con successo, totale dettagli: {}", dettagliCompleti.size());
        return new ResponseEntity<>(
            Map.of(
                "prenotazione", prenotazione,
                "dettagliCompleti", dettagliCompleti,
                "totalDettagli", dettagliCompleti.size()
            ),
            HttpStatus.OK
        );
    }

    // Vista completa di tutte le prenotazioni con dettagli - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/all-details")
    public ResponseEntity<?> getAllPrenotazioniWithDetails(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getAllPrenotazioniWithDetails");
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getAllPrenotazioniWithDetails - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero dettagli completi di tutte le prenotazioni");
        List<Map<String, Object>> dettagliCompleti = prenotazioneService.getAllCompleteDetails();
        
        logger.debug("FINE getAllPrenotazioniWithDetails - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return new ResponseEntity<>(
            Map.of(
                "prenotazioni", dettagliCompleti,
                "totalPrenotazioni", dettagliCompleti.size()
            ),
            HttpStatus.OK
        );
    }

    // Prenotazioni per stato - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/stato/{stato}")
    public ResponseEntity<?> getPrenotazioniByStato(@PathVariable String stato, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getPrenotazioniByStato - Stato: {}", stato);
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getPrenotazioniByStato - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero prenotazioni per stato: {}", stato);
        try {
            Prenotazione.StatoPrenotazione statoEnum = Prenotazione.StatoPrenotazione.valueOf(stato.toUpperCase());
            List<Prenotazione> prenotazioni = prenotazioneService.getPrenotazioniByStato(statoEnum);
            
            logger.debug("FINE getPrenotazioniByStato - Prenotazioni recuperate con successo per stato: {}, totale: {}", stato, prenotazioni.size());
            return new ResponseEntity<>(
                Map.of(
                    "stato", stato,
                    "prenotazioni", prenotazioni,
                    "totalPrenotazioni", prenotazioni.size()
                ),
                HttpStatus.OK
            );
        } catch (IllegalArgumentException e) {
            logger.debug("FINE getPrenotazioniByStato - Stato non valido: {}", stato);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Stato non valido. Stati disponibili: PRENOTATA, BLOCCATA, MANUTENZIONE, ANNULLATA"),
                HttpStatus.BAD_REQUEST
            );
        } catch (Exception e) {
            logger.error("FINE getPrenotazioniByStato - Errore interno per stato: {}, Errore: {}", stato, e.getMessage(), e);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Errore interno del server"),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Prenotazioni future - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/future")
    public ResponseEntity<?> getPrenotazioniFuture(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getPrenotazioniFuture");
        
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getPrenotazioniFuture - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero prenotazioni future");
        List<Prenotazione> prenotazioni = prenotazioneService.getPrenotazioniFuture();
        
        logger.debug("FINE getPrenotazioniFuture - Prenotazioni future recuperate con successo, totale: {}", prenotazioni.size());
        return new ResponseEntity<>(
            Map.of(
                "prenotazioni", prenotazioni,
                "totalPrenotazioni", prenotazioni.size()
            ),
            HttpStatus.OK
        );
    }
}
