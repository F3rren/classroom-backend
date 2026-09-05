package com.prenotazioni.prenotazione.controller;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.prenotazione.service.AulaService;
import com.prenotazioni.prenotazione.service.PrenotazioneService;
import com.prenotazioni.prenotazione.model.Aula;
import com.prenotazioni.dto.*;
// entrambe: in com.prenotazioni.dto restano le classi comuni di shared,
// in com.prenotazioni.prenotazione.dto quelle di questo servizio
import com.prenotazioni.prenotazione.dto.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Tutti gli endpoint sono accessibili a qualunque utente autenticato (nessun ruolo richiesto):
 * l'autenticazione stessa e' gia' garantita dalla policy anyRequest().authenticated() di
 * SecurityConfig, quindi qui non serve ne' un controllo manuale ne' @PreAuthorize.
 */
@RestController
@RequestMapping("/api/rooms")
@Tag(name = "Aule")
public class RoomController {

    private final AulaService aulaService;
    private final PrenotazioneService prenotazioneService;

    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);

    /** Tetto al filtro per capienza: oltre non e' una richiesta plausibile, e' un errore di battitura. */
    private static final int CAPIENZA_MASSIMA_RICHIEDIBILE = 1000;

    RoomController(AulaService aulaService, PrenotazioneService prenotazioneService) {
        this.aulaService = aulaService;
        this.prenotazioneService = prenotazioneService;
    }

    /** Lo stesso identificativo che vedra' il gestore degli errori, non uno diverso. */
    private String generateSessionId() {
        return RequestCorrelationFilter.corrente();
    }

    private <T> ApiEnvelope<T> createErrorResponse(String error, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(error, message, userMessage, sessionId);
    }

    private <T> ApiEnvelope<T> createSuccessResponse(String message, T data, String sessionId) {
        return ApiEnvelope.success(message, data, sessionId);
    }

    @GetMapping
    @Operation(summary = "Elenca tutte le aule")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getAllRooms() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getAllRooms - Richiesta lista completa aule");

        List<Aula> aule = aulaService.getAllAule();
        logger.debug("FINE getAllRooms - Aule recuperate con successo, totale: {}", aule.size());
        return new ResponseEntity<>(
            createSuccessResponse(aule.isEmpty() ? "Nessuna aula disponibile" : "Aule recuperate con successo",
                                RoomListPayload.of(aule), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/details")
    @Operation(summary = "Dettagli completi di tutte le prenotazioni su tutte le aule")
    public ResponseEntity<PrenotazioneDettaglioListPayload> getAllRoomsWithDetails() {
        logger.debug("INIZIO getAllRoomsWithDetails");
        List<PrenotazioneDettaglioDto> dettagliCompleti = prenotazioneService.getAllCompleteDetails();
        logger.debug("FINE getAllRoomsWithDetails - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return ResponseEntity.ok(new PrenotazioneDettaglioListPayload(dettagliCompleti));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recupera una singola aula per ID")
    public ResponseEntity<ApiEnvelope<RoomDetailAckPayload>> getRoomById(@PathVariable Long id) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getRoomById - ID Aula richiesta: {}", id);

        if (id == null || id <= 0) {
            logger.warn("FINE getRoomById - ID aula non valido: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        Optional<Aula> aula = aulaService.getAulaById(id);
        if (aula.isEmpty()) {
            logger.warn("FINE getRoomById - Aula non trovata con ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("ROOM_NOT_FOUND", "Aula not found",
                                  String.format("L'aula con ID %d non esiste nel sistema.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("FINE getRoomById - Aula recuperata con successo: ID: {}, Nome: {}", aula.get().getId(), aula.get().getNome());
        return new ResponseEntity<>(
            createSuccessResponse("Aula recuperata con successo", new RoomDetailAckPayload(aula.get()), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/{id}/details")
    @Operation(summary = "Aula con le sue prenotazioni dettagliate")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = RoomWithBookingsPayload.class)))
    public ResponseEntity<?> getRoomDetailsById(@PathVariable Long id) {
        logger.debug("INIZIO getRoomDetailsById - ID Aula: {}", id);

        Optional<Aula> aula = aulaService.getAulaById(id);
        if (aula.isEmpty()) {
            // Prima: {"error":"Aula non trovata"}, una forma diversa dall'envelope usato
            // ovunque: niente "success", niente "userMessage", e "error" con dentro una
            // frase invece di un codice.
            throw ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", id);
        }

        logger.debug("Aula trovata: ID: {}, Nome: {}", aula.get().getId(), aula.get().getNome());
        List<PrenotazioneDettaglioDto> dettagliCompleti = prenotazioneService.getRoomCompleteDetails(id);

        logger.debug("FINE getRoomDetailsById - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return ResponseEntity.ok(new RoomWithBookingsPayload(aula.get(), dettagliCompleti));
    }

    @GetMapping("/piano/{piano}")
    @Operation(summary = "Filtra aule per piano")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getRoomsByFloor(@PathVariable int piano) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getRoomsByFloor - Piano richiesto: {}", piano);

        if (piano < 0) {
            logger.warn("FINE getRoomsByFloor - Piano non valido: {}", piano);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_FLOOR", "Invalid floor",
                                  "Il numero del piano deve essere maggiore o uguale a 0.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        List<Aula> aule = aulaService.getAuleByPiano(piano);
        logger.debug("FINE getRoomsByFloor - Aule recuperate con successo per piano: {}, totale: {}", piano, aule.size());
        return new ResponseEntity<>(
            createSuccessResponse(aule.isEmpty() ? "Nessuna aula trovata per questo piano" : "Aule recuperate con successo",
                                RoomListPayload.of(aule).withPiano(piano), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/capienza")
    @Operation(summary = "Filtra aule per capienza minima")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getRoomsByCapacity(@RequestParam int minCapienza) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getRoomsByCapacity - Capienza minima richiesta: {}", minCapienza);

        if (minCapienza < 0) {
            logger.warn("FINE getRoomsByCapacity - Capienza minima non valida: {}", minCapienza);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_CAPACITY", "Invalid capacity",
                                  "La capienza minima deve essere un numero maggiore o uguale a 0.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }
        if (minCapienza > CAPIENZA_MASSIMA_RICHIEDIBILE) {
            logger.warn("FINE getRoomsByCapacity - Capienza minima troppo alta: {}", minCapienza);
            return new ResponseEntity<>(
                createErrorResponse("CAPACITY_TOO_HIGH", "Capacity above the allowed maximum",
                                  "La capienza minima richiesta è troppo alta. Inserisci un valore realistico (massimo "
                                  + CAPIENZA_MASSIMA_RICHIEDIBILE + ").", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        List<Aula> aule = aulaService.getAuleByCapienzaMinima(minCapienza);
        logger.debug("FINE getRoomsByCapacity - Aule recuperate con successo per capienza >= {}, totale: {}", minCapienza, aule.size());

        RoomListPayload payload = RoomListPayload.of(aule).withCapienzaMinima(minCapienza);
        if (aule.isEmpty()) {
            return new ResponseEntity<>(
                createSuccessResponse("Nessuna aula trovata con la capienza richiesta",
                                    payload.withSuggestion("Prova con una capienza minore"), sessionId),
                HttpStatus.OK
            );
        }
        int maxCapacityFound = aule.stream().mapToInt(Aula::getCapienza).max().orElse(0);
        return new ResponseEntity<>(
            createSuccessResponse("Aule recuperate con successo", payload.withMaxCapacityFound(maxCapacityFound), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/detailed")
    @Operation(summary = "Elenca tutte le aule con dettagli completi")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getAllRoomsDetailed() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getAllRoomsDetailed - Richiesta aule con dettagli completi");

        List<RoomDetailsResponse> roomDetails = aulaService.getAllRoomsWithDetails();
        logger.debug("FINE getAllRoomsDetailed - Aule con dettagli recuperate con successo, totale: {}", roomDetails.size());
        return new ResponseEntity<>(
            createSuccessResponse(roomDetails.isEmpty() ? "Nessuna aula con dettagli disponibile" : "Aule con dettagli recuperate con successo",
                                RoomListPayload.of(roomDetails), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/{id}/detailed")
    @Operation(summary = "Recupera una singola aula con dettagli completi")
    public ResponseEntity<ApiEnvelope<RoomWrapper<RoomDetailsResponse>>> getRoomDetailed(@PathVariable Long id) {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getRoomDetailed - ID Aula: {}", id);

        if (id == null || id <= 0) {
            logger.warn("FINE getRoomDetailed - ID aula non valido: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_ROOM_ID", "Invalid aula id",
                                  "L'ID dell'aula deve essere un numero positivo maggiore di 0", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        RoomDetailsResponse roomDetails = aulaService.getRoomWithDetails(id);
        logger.debug("FINE getRoomDetailed - Dettagli aula recuperati con successo: ID: {}, Nome: {}", roomDetails.getId(), roomDetails.getName());
        return new ResponseEntity<>(
            createSuccessResponse("Dettagli aula recuperati con successo", new RoomWrapper<>(roomDetails), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/physical")
    @Operation(summary = "Elenca solo le aule fisiche")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getPhysicalRooms() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getPhysicalRooms - Richiesta aule fisiche");

        List<Aula> aule = aulaService.getPhysicalRoomsOrdered();
        logger.debug("FINE getPhysicalRooms - Aule fisiche recuperate con successo, totale: {}", aule.size());
        return new ResponseEntity<>(
            createSuccessResponse(aule.isEmpty() ? "Nessuna aula fisica disponibile" : "Aule fisiche recuperate con successo",
                                RoomListPayload.of(aule).withType("physical"), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/virtual")
    @Operation(summary = "Elenca solo le aule virtuali")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getVirtualRooms() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getVirtualRooms - Richiesta aule virtuali");

        List<Aula> aule = aulaService.getVirtualRoomsOrdered();
        logger.debug("FINE getVirtualRooms - Aule virtuali recuperate con successo, totale: {}", aule.size());
        return new ResponseEntity<>(
            createSuccessResponse(aule.isEmpty() ? "Nessuna aula virtuale disponibile" : "Aule virtuali recuperate con successo",
                                RoomListPayload.of(aule).withType("virtual"), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/physical/detailed")
    @Operation(summary = "Aule fisiche con dettagli completi")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getPhysicalRoomsDetailed() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getPhysicalRoomsDetailed - Richiesta aule fisiche con dettagli");

        List<RoomDetailsResponse> roomDetails = aulaService.getPhysicalRoomsWithDetails();
        logger.debug("FINE getPhysicalRoomsDetailed - Aule fisiche con dettagli recuperate con successo, totale: {}", roomDetails.size());
        return new ResponseEntity<>(
            createSuccessResponse(roomDetails.isEmpty() ? "Nessuna aula fisica con dettagli disponibile" : "Aule fisiche con dettagli recuperate con successo",
                                RoomListPayload.of(roomDetails).withType("physical"), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/virtual/detailed")
    @Operation(summary = "Aule virtuali con dettagli completi")
    public ResponseEntity<ApiEnvelope<RoomListPayload>> getVirtualRoomsDetailed() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getVirtualRoomsDetailed - Richiesta aule virtuali con dettagli");

        List<RoomDetailsResponse> roomDetails = aulaService.getVirtualRoomsWithDetails();
        logger.debug("FINE getVirtualRoomsDetailed - Aule virtuali con dettagli recuperate con successo, totale: {}", roomDetails.size());
        return new ResponseEntity<>(
            createSuccessResponse(roomDetails.isEmpty() ? "Nessuna aula virtuale con dettagli disponibile" : "Aule virtuali con dettagli recuperate con successo",
                                RoomListPayload.of(roomDetails).withType("virtual"), sessionId),
            HttpStatus.OK
        );
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiche aule fisiche vs virtuali")
    public ResponseEntity<ApiEnvelope<RoomStatsPayload>> getRoomsStats() {
        String sessionId = generateSessionId();
        logger.debug("INIZIO getRoomsStats - Richiesta statistiche aule");

        long physicalCount = aulaService.countPhysicalRooms();
        long virtualCount = aulaService.countVirtualRooms();
        RoomStats stats = new RoomStats(physicalCount, virtualCount);

        logger.debug("FINE getRoomsStats - Statistiche calcolate: Totale: {}, Fisiche: {}, Virtuali: {}", stats.getTotalRooms(), physicalCount, virtualCount);

        return new ResponseEntity<>(
            createSuccessResponse("Statistiche aule recuperate con successo", new RoomStatsPayload(stats), sessionId),
            HttpStatus.OK
        );
    }
}
