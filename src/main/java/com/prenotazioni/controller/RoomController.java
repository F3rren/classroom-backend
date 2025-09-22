package com.prenotazioni.controller;

import com.prenotazioni.service.JwtService;
import com.prenotazioni.service.AulaService;
import com.prenotazioni.service.PrenotazioneService;
import com.prenotazioni.model.Aula;
import com.prenotazioni.dto.RoomDetailsResponse;

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
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private AulaService aulaService;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private PrenotazioneService prenotazioneService;

    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);

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

    // Lista tutte le aule - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping
    public ResponseEntity<?> getAllRooms(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getAllRooms");
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getAllRooms - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero tutte le aule");
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
    }

    // Vista completa di tutte le prenotazioni - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/details")
    public ResponseEntity<?> getAllRoomsWithDetails(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getAllRoomsWithDetails");
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getAllRoomsWithDetails - Autenticazione fallita");
            return authCheck;
        }
        logger.debug("Autenticazione riuscita, recupero dettagli completi delle prenotazioni");
        List<Map<String, Object>> dettagliCompleti = prenotazioneService.getAllCompleteDetails();
        
        logger.debug("FINE getAllRoomsWithDetails - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return new ResponseEntity<>(
            Map.of(
                "prenotazioni", dettagliCompleti,
                "totalPrenotazioni", dettagliCompleti.size()
            ),
            HttpStatus.OK
        );
    }

    // Ottieni singola aula per ID - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI  
    @GetMapping("/{id}")
    public ResponseEntity<?> getRoomById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getRoomById - ID Aula: {}", id);
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getRoomById - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero aula con ID: {}", id);
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
    }

    // Ottieni dettagli completi aula con prenotazioni - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/{id}/details")
    public ResponseEntity<?> getRoomDetailsById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getRoomDetailsById - ID Aula: {}", id);
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getRoomDetailsById - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero dettagli completi per aula con ID: {}", id);
        // Prima verifica se l'aula esiste
        java.util.Optional<Aula> aula = aulaService.getAulaById(id);
        if (aula.isEmpty()) {
            logger.debug("FINE getRoomDetailsById - Aula non trovata con ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Aula non trovata"),
                HttpStatus.NOT_FOUND
            );
        }

        logger.debug("Aula trovata: ID: {}, Nome: {}", aula.get().getId(), aula.get().getNome());
        // Ottieni i dettagli completi
        List<Map<String, Object>> dettagliCompleti = prenotazioneService.getRoomCompleteDetails(id);
        
        logger.debug("FINE getRoomDetailsById - Dettagli completi recuperati con successo, totale prenotazioni: {}", dettagliCompleti.size());
        return new ResponseEntity<>(
            Map.of(
                "aula", aula.get(),
                "prenotazioni", dettagliCompleti,
                "totalPrenotazioni", dettagliCompleti.size()
            ),
            HttpStatus.OK
        );
    }

    // Filtra aule per piano - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/piano/{piano}")
    public ResponseEntity<?> getRoomsByFloor(@PathVariable int piano, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getRoomsByFloor - Piano: {}", piano);
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getRoomsByFloor - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero aule per piano: {}", piano);
        List<Aula> aule = aulaService.getAuleByPiano(piano);
        if (aule == null || aule.isEmpty()) {
            logger.debug("FINE getRoomsByFloor - Nessuna aula trovata per il piano: {}", piano);
            return new ResponseEntity<>(
                Collections.singletonMap("message", "Nessuna aula trovata per il piano " + piano),
                HttpStatus.OK
            );
        }
        logger.debug("FINE getRoomsByFloor - Aule recuperate con successo per il piano: {}, totale: {}", piano, aule.size());
        return new ResponseEntity<>(
            Collections.singletonMap("rooms", aule),
            HttpStatus.OK
        );
    }

    // Filtra aule per capienza minima - ACCESSIBILE A TUTTI GLI UTENTI AUTENTICATI
    @GetMapping("/capienza")
    public ResponseEntity<?> getRoomsByCapacity(@RequestParam int minCapienza, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getRoomsByCapacity - Capienza minima: {}", minCapienza);
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getRoomsByCapacity - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero aule con capienza minima di: {}", minCapienza);
        List<Aula> aule = aulaService.getAuleByCapienzaMinima(minCapienza);
        if (aule == null || aule.isEmpty()) {
            logger.debug("FINE getRoomsByCapacity - Nessuna aula trovata con capienza >= {}", minCapienza);
            return new ResponseEntity<>(
                Collections.singletonMap("message", "Nessuna aula trovata con capienza >= " + minCapienza),
                HttpStatus.OK
            );
        }

        logger.debug("FINE getRoomsByCapacity - Aule recuperate con successo, totale: {}", aule.size());
        return new ResponseEntity<>(
            Collections.singletonMap("rooms", aule),
            HttpStatus.OK
        );
    }

    // Endpoint per ottenere tutte le aule con dettagli completi (formato mock-like)
    @GetMapping("/detailed")
    public ResponseEntity<?> getAllRoomsDetailed(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getAllRoomsDetailed");
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getAllRoomsDetailed - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero tutte le aule con dettagli completi");
        List<RoomDetailsResponse> roomDetails = aulaService.getAllRoomsWithDetails();
        logger.debug("FINE getAllRoomsDetailed - Aule con dettagli completi recuperate con successo, totale: {}", roomDetails.size());
        return new ResponseEntity<>(
            Collections.singletonMap("rooms", roomDetails),
            HttpStatus.OK
        );
    }

    // Endpoint per ottenere una singola aula con dettagli completi
    @GetMapping("/{id}/detailed")
    public ResponseEntity<?> getRoomDetailed(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getRoomDetailed - ID Aula: {}", id);
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getRoomDetailed - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero dettagli aula - ID: {}", id);
        RoomDetailsResponse roomDetails = aulaService.getRoomWithDetails(id);
        if (roomDetails == null) {
            logger.debug("FINE getRoomDetailed - Aula non trovata con ID: {}", id);
            return new ResponseEntity<>(
                Collections.singletonMap("error", "Aula non trovata"),
                HttpStatus.NOT_FOUND
            );
        }
        
        logger.debug("FINE getRoomDetailed - Dettagli aula recuperati con successo: ID: {}, Nome: {}", roomDetails.getId(), roomDetails.getName());
        return new ResponseEntity<>(
            Collections.singletonMap("room", roomDetails),
            HttpStatus.OK
        );
    }

    // Endpoint per ottenere solo le aule fisiche
    @GetMapping("/physical")
    public ResponseEntity<?> getPhysicalRooms(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getPhysicalRooms");
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getPhysicalRooms - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero aule fisiche");
        List<Aula> aule = aulaService.getPhysicalRoomsOrdered();
        logger.debug("FINE getPhysicalRooms - Aule fisiche recuperate con successo, totale: {}", aule.size());
        return new ResponseEntity<>(
            Map.of(
                "rooms", aule,
                "totalRooms", aule.size(),
                "type", "physical"
            ),
            HttpStatus.OK
        );
    }

    // Endpoint per ottenere solo le aule virtuali
    @GetMapping("/virtual")
    public ResponseEntity<?> getVirtualRooms(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getVirtualRooms");
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getVirtualRooms - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero aule virtuali");
        List<Aula> aule = aulaService.getVirtualRoomsOrdered();
        logger.debug("FINE getVirtualRooms - Aule virtuali recuperate con successo, totale: {}", aule.size());
        return new ResponseEntity<>(
            Map.of(
                "rooms", aule,
                "totalRooms", aule.size(),
                "type", "virtual"
            ),
            HttpStatus.OK
        );
    }

    // Endpoint per ottenere aule fisiche con dettagli completi
    @GetMapping("/physical/detailed")
    public ResponseEntity<?> getPhysicalRoomsDetailed(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getPhysicalRoomsDetailed");
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getPhysicalRoomsDetailed - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero aule fisiche con dettagli completi");
        List<RoomDetailsResponse> roomDetails = aulaService.getPhysicalRoomsWithDetails();
        logger.debug("FINE getPhysicalRoomsDetailed - Aule fisiche con dettagli completi recuperate con successo, totale: {}", roomDetails.size());
        return new ResponseEntity<>(
            Map.of(
                "rooms", roomDetails,
                "totalRooms", roomDetails.size(),
                "type", "physical"
            ),
            HttpStatus.OK
        );
    }

    // Endpoint per ottenere aule virtuali con dettagli completi
    @GetMapping("/virtual/detailed")
    public ResponseEntity<?> getVirtualRoomsDetailed(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getVirtualRoomsDetailed");
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getVirtualRoomsDetailed - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, recupero aule virtuali con dettagli completi");
        List<RoomDetailsResponse> roomDetails = aulaService.getVirtualRoomsWithDetails();
        logger.debug("FINE getVirtualRoomsDetailed - Aule virtuali con dettagli completi recuperate con successo, totale: {}", roomDetails.size());
        return new ResponseEntity<>(
            Map.of(
                "rooms", roomDetails,
                "totalRooms", roomDetails.size(),
                "type", "virtual"
            ),
            HttpStatus.OK
        );
    }

    // Endpoint per ottenere statistiche aule fisiche vs virtuali
    @GetMapping("/stats")
    public ResponseEntity<?> getRoomsStats(@RequestHeader("Authorization") String authHeader) {
        logger.debug("INIZIO getRoomsStats");
        ResponseEntity<?> authCheck = checkAuth(authHeader);
        if (authCheck != null) {
            logger.debug("FINE getRoomsStats - Autenticazione fallita");
            return authCheck;
        }

        logger.debug("Autenticazione riuscita, calcolo statistiche aule");
        long physicalCount = aulaService.countPhysicalRooms();
        long virtualCount = aulaService.countVirtualRooms();
        long totalCount = physicalCount + virtualCount;

        logger.debug("FINE getRoomsStats - Statistiche calcolate: Totale: {}, Fisiche: {}, Virtuali: {}", totalCount, physicalCount, virtualCount);
        return new ResponseEntity<>(
            Map.of(
                "totalRooms", totalCount,
                "physicalRooms", physicalCount,
                "virtualRooms", virtualCount,
                "physicalPercentage", totalCount > 0 ? Math.round((physicalCount * 100.0) / totalCount) : 0,
                "virtualPercentage", totalCount > 0 ? Math.round((virtualCount * 100.0) / totalCount) : 0
            ),
            HttpStatus.OK
        );
    }
}
