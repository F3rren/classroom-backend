package com.prenotazioni.prenotazione.controller.admin;

import com.prenotazioni.prenotazione.service.AulaService;
import com.prenotazioni.prenotazione.service.PrenotazioneService;
import com.prenotazioni.eventi.PrenotazioneCancellataEvento;
import com.prenotazioni.prenotazione.messaggistica.PubblicatoreEventi;
import com.prenotazioni.dto.*;
// entrambe: in com.prenotazioni.dto restano le classi comuni di shared,
// in com.prenotazioni.prenotazione.dto quelle di questo servizio
import com.prenotazioni.prenotazione.dto.*;
import com.prenotazioni.prenotazione.model.Aula;
import com.prenotazioni.prenotazione.model.Prenotazione;
import com.prenotazioni.prenotazione.model.ProprietarioPrenotazione;
import com.prenotazioni.prenotazione.model.StatoPrenotazione;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.util.LogSanitizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Tutti gli endpoint richiedono ruolo admin: applicato una sola volta a livello di classe
 * invece del controllo manuale checkAdminAccess() ripetuto in ogni metodo.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Amministrazione")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final AulaService aulaService;
    private final PrenotazioneService prenotazioneService;
    private final PubblicatoreEventi pubblicatoreEventi;

    AdminController(AulaService aulaService, PrenotazioneService prenotazioneService,
                     PubblicatoreEventi pubblicatoreEventi) {
        this.aulaService = aulaService;
        this.prenotazioneService = prenotazioneService;
        this.pubblicatoreEventi = pubblicatoreEventi;
    }

    private String generateSessionId() {
        return "ADM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private <T> ApiEnvelope<T> createErrorResponse(String errorCode, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(errorCode, message, userMessage, sessionId);
    }

    private <T> ApiEnvelope<T> createSuccessResponse(String message, T data, String sessionId) {
        return ApiEnvelope.success(message, data, sessionId);
    }

    // ==================== USER MANAGEMENT ENDPOINTS ====================


    @GetMapping("/rooms")
    @Operation(summary = "Elenca tutte le aule (solo admin)")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getAllRooms() {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO getAllRooms (admin) - Richiesta lista completa aule", sessionId);

        List<Aula> aule = aulaService.getAllAule();
        logger.debug("[{}] FINE getAllRooms - Aule recuperate con successo, totale: {}", sessionId, aule.size());
        return new ResponseEntity<>(
            createSuccessResponse(aule.isEmpty() ? "Nessuna aula presente nel sistema" : "Lista aule recuperata con successo",
                                RoomListPayload.of(aule), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/rooms/{id}")
    @Operation(summary = "Recupera una singola aula per ID (solo admin)")
    public ResponseEntity<ApiEnvelope<RoomWrapper<Aula>>> getRoomById(@PathVariable Long id) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO getRoomById (admin) - ID Aula: {}", sessionId, id);

        if (id == null || id <= 0) {
            logger.warn("[{}] FINE getRoomById - ID aula non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "ID aula non valido",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Optional<Aula> aula = aulaService.getAulaById(id);
        if (aula.isEmpty()) {
            logger.warn("[{}] FINE getRoomById - Aula non trovata con ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("ROOM_NOT_FOUND", "Aula non trovata",
                                  String.format("L'aula con ID %d non esiste.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("[{}] FINE getRoomById - Aula recuperata con successo: ID: {}, Nome: {}",
                   sessionId, aula.get().getId(), aula.get().getNome());
        return new ResponseEntity<>(
            createSuccessResponse("Aula recuperata con successo", new RoomWrapper<>(aula.get()), sessionId),
            HttpStatus.OK
        );
    }

    @PostMapping("/createrooms")
    @Operation(summary = "Crea una nuova aula (solo admin)")
    public ResponseEntity<ApiEnvelope<RoomAckPayload>> createRoom(@Valid @RequestBody AulaRequest roomRequest) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO createRoom | Nome: {} | Piano: {} | Capienza: {}",
                   sessionId, roomRequest.getNome(), roomRequest.getPiano(), roomRequest.getCapienza());

        Aula nuovaAula = aulaService.createAula(roomRequest);
        if (nuovaAula == null) {
            logger.warn("[{}] FINE createRoom - Impossibile creare aula | Nome: {}", sessionId, roomRequest.getNome());
            return new ResponseEntity<>(
                createErrorResponse("ROOM_CREATION_FAILED", "Impossibile creare aula",
                                  String.format("Impossibile creare l'aula '%s'. Verifica che il nome non sia già esistente.",
                                               roomRequest.getNome()), sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        logger.debug("[{}] FINE createRoom - Aula creata con successo | ID: {} | Nome: {}",
                   sessionId, nuovaAula.getId(), nuovaAula.getNome());
        return new ResponseEntity<>(
            createSuccessResponse("Aula creata con successo", new RoomAckPayload(nuovaAula), sessionId),
            HttpStatus.CREATED
        );
    }

    @PutMapping("/rooms/{id}")
    @Operation(summary = "Modifica un'aula esistente (solo admin)")
    public ResponseEntity<ApiEnvelope<RoomAckPayload>> updateRoom(@PathVariable Long id, @Valid @RequestBody AulaRequest roomRequest) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO updateRoom | ID Aula: {} | Nuovo Nome: {} | Piano: {} | Capienza: {}",
                   sessionId, id, roomRequest.getNome(), roomRequest.getPiano(), roomRequest.getCapienza());

        if (id == null || id <= 0) {
            logger.warn("[{}] FINE updateRoom - ID aula non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "ID aula non valido",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Aula aulaAggiornata = aulaService.updateAula(id, roomRequest);
        if (aulaAggiornata == null) {
            logger.warn("[{}] FINE updateRoom - Impossibile aggiornare aula ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("ROOM_UPDATE_FAILED", "Impossibile aggiornare aula",
                                  String.format("L'aula con ID %d non esiste o non può essere aggiornata.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("[{}] FINE updateRoom - Aula aggiornata con successo | ID: {} | Nome: {}",
                   sessionId, aulaAggiornata.getId(), aulaAggiornata.getNome());
        return new ResponseEntity<>(
            createSuccessResponse("Aula aggiornata con successo", new RoomAckPayload(aulaAggiornata), sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/rooms/{id}")
    @Operation(summary = "Elimina un'aula (solo admin)")
    public ResponseEntity<ApiEnvelope<DeletedRoomResponse>> deleteRoom(@PathVariable Long id) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO deleteRoom - ID Aula: {}", sessionId, id);

        if (id == null || id <= 0) {
            logger.warn("[{}] FINE deleteRoom - ID aula non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "ID aula non valido",
                                  "L'ID dell'aula deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        boolean eliminata = aulaService.deleteAula(id);
        if (!eliminata) {
            logger.warn("[{}] FINE deleteRoom - Impossibile eliminare aula ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("ROOM_NOT_FOUND", "Impossibile eliminare aula",
                                  String.format("L'aula con ID %d non esiste o non può essere eliminata.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("[{}] FINE deleteRoom - Aula eliminata con successo - ID: {}", sessionId, id);
        return new ResponseEntity<>(
            createSuccessResponse("Aula eliminata con successo", new DeletedRoomResponse(id), sessionId),
            HttpStatus.OK
        );
    }

    // ========== GESTIONE PRENOTAZIONI ADMIN ==========

    @GetMapping("/prenotazioni")
    @Operation(summary = "Elenca tutte le prenotazioni, incluse annullate (solo admin)")
    public ResponseEntity<ApiEnvelope<AdminPrenotazioniPayload>> getAllPrenotazioniAdmin() {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO getAllPrenotazioniAdmin", sessionId);

        List<Prenotazione> tuttePrenotazioni = prenotazioneService.getAllPrenotazioni();
        long attive = tuttePrenotazioni.stream()
            .filter(p -> p.getStato() != StatoPrenotazione.ANNULLATA)
            .count();
        long annullate = tuttePrenotazioni.size() - attive;

        logger.debug("[{}] FINE getAllPrenotazioniAdmin - Totale: {} (Attive: {}, Annullate: {})",
                   sessionId, tuttePrenotazioni.size(), attive, annullate);

        AdminPrenotazioniPayload payload = new AdminPrenotazioniPayload(
            tuttePrenotazioni, new PrenotazioniStats(tuttePrenotazioni.size(), attive, annullate));

        return new ResponseEntity<>(
            createSuccessResponse("Prenotazioni recuperate con successo", payload, sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/prenotazioni/{id}")
    @Operation(summary = "Elimina forzatamente qualsiasi prenotazione (solo admin)")
    public ResponseEntity<ApiEnvelope<BookingDeletionResponse>> deletePrenotazioneAsAdmin(@PathVariable Long id,
                                                      @AuthenticationPrincipal AppPrincipal principal,
                                                      @Valid @RequestBody(required = false) DeleteReasonRequest requestBody) {
        String sessionId = generateSessionId();
        logger.debug("[{}] INIZIO deletePrenotazioneAsAdmin - ID Prenotazione: {}", sessionId, id);

        if (id == null || id <= 0) {
            logger.warn("[{}] FINE deletePrenotazioneAsAdmin - ID prenotazione non valido: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_BOOKING_ID", "ID prenotazione non valido",
                                  "L'ID della prenotazione deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Long adminId = principal.id();
        logger.info("[{}] Admin ID: {} tenta di eliminare prenotazione: {}", sessionId, adminId, id);

        Prenotazione prenotazione = prenotazioneService.getPrenotazioneById(id);
        if (prenotazione == null) {
            logger.warn("[{}] FINE deletePrenotazioneAsAdmin - Prenotazione non trovata ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("BOOKING_NOT_FOUND", "Prenotazione non trovata",
                                  String.format("La prenotazione con ID %d non esiste.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        ProprietarioPrenotazione utentePrenotazione = prenotazione.getUtente();
        Aula aulaPrenotazione = prenotazione.getAula();

        String motivo = (requestBody != null && requestBody.getReason() != null)
            ? requestBody.getReason()
            : "Eliminazione da parte dell'amministratore";
        logger.debug("[{}] Motivo eliminazione: {}", sessionId, motivo);

        boolean eliminata = prenotazioneService.annullaPrenotazioneAsAdmin(id, adminId, motivo);
        if (!eliminata) {
            logger.warn("[{}] FINE deletePrenotazioneAsAdmin - Impossibile eliminare prenotazione ID: {}", sessionId, id);
            return new ResponseEntity<>(
                createErrorResponse("BOOKING_DELETION_FAILED", "Impossibile eliminare prenotazione",
                                  String.format("La prenotazione con ID %d non può essere eliminata.", id), sessionId),
                HttpStatus.CONFLICT
            );
        }

        try {
            // Il nome dell'admin arriva dal token: chiederlo ad auth-service significherebbe
            // una chiamata di rete per compilare il testo di una notifica.
            String adminNome = principal.nome() != null ? principal.nome() : "Amministratore";
            String dataPrenotazione = prenotazione.getInizio().toLocalDate().toString();
            String oraInizio = prenotazione.getInizio().toLocalTime().toString();
            String oraFine = prenotazione.getFine().toLocalTime().toString();
            String nomeStanza = aulaPrenotazione != null ? aulaPrenotazione.getNome() : "Stanza non specificata";

            // Pubblicato su coda e non chiamato via REST: cosi' la notifica non si perde
            // se notifica-service e' spento. Il record tipizzato ha anche sostituito la
            // mappa di stringhe che c'era prima, dove un nome di campo sbagliato sarebbe
            // arrivato a destinazione come semplice valore mancante.
            pubblicatoreEventi.pubblicaCancellazione(new PrenotazioneCancellataEvento(
                    utentePrenotazione.getId(), id, nomeStanza, adminNome,
                    dataPrenotazione, oraInizio, oraFine, motivo));

            logger.debug("[{}] Notifica di cancellazione creata per utente: {}", sessionId, utentePrenotazione.getId());
        } catch (Exception e) {
            logger.error("[{}] Errore durante creazione notifica per utente: {} | Errore: {}",
                       sessionId, utentePrenotazione.getId(), e.getMessage(), e);
            // Non blocchiamo l'operazione se la notifica fallisce
        }

        logger.debug("[{}] FINE deletePrenotazioneAsAdmin - Prenotazione eliminata con successo | ID: {} | Admin: {} | Motivo: {}",
                   sessionId, id, adminId, motivo);

        return new ResponseEntity<>(
            createSuccessResponse("Prenotazione eliminata con successo dall'amministratore",
                                new BookingDeletionResponse(id, adminId, motivo), sessionId),
            HttpStatus.OK
        );
    }
}
