package com.classroom.auth.controller;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.util.LogSanitizer;
import com.classroom.dto.ApiEnvelope;
import com.classroom.auth.dto.UserSummaryDto;
import com.classroom.auth.model.User;
import com.classroom.auth.repository.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/api/me")
@Tag(name = "Profilo")
public class MeController {

    private final UserRepository userRepository;

    MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The same id the error handler will see, not a different one. */
    private String generateSessionId() {
        return RequestCorrelationFilter.current();
    }

    @GetMapping
    @Operation(summary = "Profile of the authenticated user")
    public ResponseEntity<ApiEnvelope<UserSummaryDto>> getMe(Authentication authentication) {
        String sessionId = generateSessionId();
        logger.debug("START getMe - profile information requested");

        // If the request got this far, Spring Security has already guaranteed a valid
        // principal (filter-level refusals are handled by ApiAuthenticationEntryPoint,
        // before the dispatch)..
        String email = authentication.getName().trim().toLowerCase();

        User user = userRepository.findByEmail(email);
        if (user == null) {
            logger.warn("getMe - no user in the database for {}", LogSanitizer.maskEmail(email));
            return new ResponseEntity<>(
                    ApiEnvelope.error("USER_NOT_FOUND", "Utente not found",
                            "Nessun utente trovato con le tue credenziali. Effettua nuovamente il login.", sessionId),
                    HttpStatus.NOT_FOUND
            );
        }

        logger.debug("END getMe - profile fetched | ID: {} | email: {}", user.getId(), email);

        return new ResponseEntity<>(
                ApiEnvelope.success("Profilo utente recuperato con successo", UserSummaryDto.forProfile(user), sessionId),
                HttpStatus.OK
        );
    }
}
