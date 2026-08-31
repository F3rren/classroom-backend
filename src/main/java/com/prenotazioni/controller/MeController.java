package com.prenotazioni.controller;

import com.prenotazioni.util.LogSanitizer;
import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.dto.UserSummaryDto;
import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IUtenteRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Profilo")
public class MeController {

    private static final Logger logger = LoggerFactory.getLogger(MeController.class);

    private final IUtenteRepository utenteRepository;

    MeController(IUtenteRepository utenteRepository) {
        this.utenteRepository = utenteRepository;
    }

    private String generateSessionId() {
        return "ME_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @GetMapping
    @Operation(summary = "Profilo dell'utente autenticato")
    public ResponseEntity<ApiEnvelope<UserSummaryDto>> getMe(Authentication authentication) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO getMe - Richiesta informazioni profilo utente", sessionId);

        // Se la richiesta e' arrivata qui, Spring Security ha gia' garantito un principal valido
        // (rifiuti a livello di filtro sono gestiti da ApiAuthenticationEntryPoint, prima del dispatch).
        String email = authentication.getName().trim().toLowerCase();

        Utente utente = utenteRepository.findByEmail(email);
        if (utente == null) {
            logger.warn("[{}] getMe - nessun utente in database per {}", sessionId, LogSanitizer.maskEmail(email));
            return new ResponseEntity<>(
                    ApiEnvelope.error("USER_NOT_FOUND", "Utente non trovato",
                            "Nessun utente trovato con le tue credenziali. Effettua nuovamente il login.", sessionId),
                    HttpStatus.NOT_FOUND
            );
        }

        logger.debug("[{}] FINE getMe - Profilo utente recuperato con successo | ID: {} | Email: {}",
                sessionId, utente.getId(), email);

        return new ResponseEntity<>(
                ApiEnvelope.success("Profilo utente recuperato con successo", UserSummaryDto.forProfile(utente), sessionId),
                HttpStatus.OK
        );
    }
}
