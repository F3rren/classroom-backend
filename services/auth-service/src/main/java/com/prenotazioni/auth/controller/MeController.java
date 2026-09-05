package com.prenotazioni.auth.controller;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.util.LogSanitizer;
import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.auth.dto.UserSummaryDto;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.auth.repository.UserRepository;

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


@RestController
@RequestMapping("/api/me")
@Tag(name = "Profilo")
public class MeController {

    private static final Logger logger = LoggerFactory.getLogger(MeController.class);

    private final UserRepository userRepository;

    MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Lo stesso identificativo che vedra' il gestore degli errori, non uno diverso. */
    private String generateSessionId() {
        return RequestCorrelationFilter.current();
    }

    @GetMapping
    @Operation(summary = "Profilo dell'utente autenticato")
    public ResponseEntity<ApiEnvelope<UserSummaryDto>> getMe(Authentication authentication) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getMe - Richiesta informazioni profilo utente");

        // Se la richiesta e' arrivata qui, Spring Security ha gia' garantito un principal valido
        // (rifiuti a livello di filtro sono gestiti da ApiAuthenticationEntryPoint, prima del dispatch).
        String email = authentication.getName().trim().toLowerCase();

        User user = userRepository.findByEmail(email);
        if (user == null) {
            logger.warn("getMe - nessun utente in database per {}", LogSanitizer.maskEmail(email));
            return new ResponseEntity<>(
                    ApiEnvelope.error("USER_NOT_FOUND", "Utente not found",
                            "Nessun utente trovato con le tue credenziali. Effettua nuovamente il login.", sessionId),
                    HttpStatus.NOT_FOUND
            );
        }

        logger.debug("FINE getMe - Profilo utente recuperato con successo | ID: {} | Email: {}", user.getId(), email);

        return new ResponseEntity<>(
                ApiEnvelope.success("Profilo utente recuperato con successo", UserSummaryDto.forProfile(user), sessionId),
                HttpStatus.OK
        );
    }
}
