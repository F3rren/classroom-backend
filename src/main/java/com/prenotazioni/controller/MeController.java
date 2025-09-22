package com.prenotazioni.controller;

import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.UtenteRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {
    
    private static final Logger logger = LoggerFactory.getLogger(MeController.class);
    
    @Autowired
    private UtenteRepository utenteRepository;
  

    @GetMapping
    public ResponseEntity<?> getMe(Authentication authentication) {
        logger.debug("INIZIO getMe");
        String email = (String) authentication.getPrincipal();
        
        logger.info("Richiesta informazioni profilo utente: {}", email);
        logger.info("Operazione di autenticazione - Utente: {}, Tipo: {}, Esito: {}", email, "GET_PROFILE", true);
        
        Utente utente = utenteRepository.findByEmail(email);
        
        if (utente == null) {
            logger.warn("Utente non trovato nel database: {}", email);
            logger.info("Operazione di autenticazione - Utente: {}, Tipo: {}, Esito: {}", email, "GET_PROFILE", false);
            return ResponseEntity.notFound().build();
        }
        
        logger.debug("Profilo utente recuperato con successo - ID: {}, Nome: {}", utente.getId(), utente.getNome());
        logger.info("Operazione di autenticazione - Utente: {}, Tipo: {}, Esito: {}", email, "GET_PROFILE", true);
        
        logger.debug("FINE getMe");
        return ResponseEntity.ok(utente);
    }
}
