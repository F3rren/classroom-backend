package com.prenotazioni.exception;

import org.springframework.web.servlet.NoHandlerFoundException;
import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.dto.ApiEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

/**
 * Punto unico di gestione errori per i controller migrati al nuovo pattern
 * (Authentication/@PreAuthorize + ApiEnvelope). Sostituisce i metodi privati
 * createErrorResponse/generateSessionId duplicati in 4 controller.
 *
 * Copre solo le eccezioni sollevate DENTRO l'esecuzione del metodo del controller.
 * I rifiuti a livello di filtro di sicurezza (nessun token / token non valido) sono
 * gestiti separatamente da ApiAuthenticationEntryPoint/ApiAccessDeniedHandler, perche'
 * avvengono prima che il dispatch al controller (e quindi questo @RestControllerAdvice)
 * abbia luogo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * L'identificativo della richiesta in corso, non uno nuovo.
     *
     * Prima questo metodo ne generava uno proprio, quindi la stessa richiesta compariva
     * nei log con due identificativi diversi: quello del controller e quello prodotto
     * qui rispondendo. Erano scollegati, e non c'era modo di sapere che appartenessero
     * alla stessa chiamata.
     */
    private String newSessionId() {
        return RequestCorrelationFilter.current();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String sessionId = newSessionId();
        FieldError firstError = ex.getBindingResult().getFieldError();
        String userMessage = firstError != null
                ? firstError.getDefaultMessage()
                : "I dati inviati non sono validi.";
        logger.warn("Validazione fallita: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error("VALIDATION_ERROR", "Request body failed validation", userMessage, sessionId),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAccessDenied(AccessDeniedException ex) {
        String sessionId = newSessionId();
        // Se il messaggio e' stato costruito esplicitamente da un throw applicativo, lo si preserva;
        // altrimenti (es. rifiuto generato da @PreAuthorize) si usa un messaggio equivalente a quello
        // gia' in uso oggi per gli accessi admin negati.
        String userMessage = ex.getMessage() != null
                ? ex.getMessage()
                : "Accesso negato: privilegi insufficienti per questa operazione.";
        logger.warn("Accesso negato: {}", userMessage);
        return new ResponseEntity<>(
                ApiEnvelope.error("ACCESS_DENIED", "Access denied", userMessage, sessionId),
                HttpStatus.FORBIDDEN
        );
    }

    /**
     * Risorsa inesistente. Prima questo caso arrivava qui come null, e ogni controller
     * decideva da se' che fosse un 404 e con quale messaggio: sedici controlli "== null"
     * sparsi, ognuno con la sua frase scritta a mano.
     */
    /**
     * Dato non accettabile che Bean Validation non poteva intercettare, tipicamente perche'
     * dipende dal dominio. Volutamente NON si mappa qui IllegalArgumentException: quella
     * segnala un errore di programmazione, e trasformarla in 400 farebbe passare i bug del
     * server per colpa di chi chiama.
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleInvalidRequest(InvalidRequestException ex) {
        String sessionId = newSessionId();
        // WARN e non DEBUG: e' un 400, come la validazione qui sopra, e per la stessa
        // ragione - un corpo che non rispetta il contratto dice qualcosa su chi chiama.
        // Erano su due livelli diversi senza motivo.
        logger.warn("Richiesta non valida: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        String sessionId = newSessionId();
        // debug e non warn: una risorsa cercata e non trovata e' un esito normale
        // dell'uso dell'applicazione, non un sintomo di qualcosa che non va.
        logger.debug("Risorsa non trovata: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.NOT_FOUND
        );
    }

    /**
     * Conflitto con lo stato attuale dei dati. Cattura anche BookingConflictException, che
     * ne e' un sottotipo: il gestore piu' specifico qui sotto resta comunque preferito da
     * Spring, e serve a distinguere il caso nei log.
     */
    @ExceptionHandler(DomainConflictException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDomainConflict(DomainConflictException ex) {
        String sessionId = newSessionId();
        logger.warn("Conflitto di dominio: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleBookingConflict(BookingConflictException ex) {
        String sessionId = newSessionId();
        logger.warn("Conflitto prenotazione: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String sessionId = newSessionId();
        logger.warn("Vincolo del database violato: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error("CONFLICT", "Conflict with the current state of the data",
                        "L'operazione non e' andata a buon fine per un conflitto con dati esistenti.", sessionId),
                HttpStatus.CONFLICT
        );
    }

    /**
     * Una risorsa inesistente deve restare un 404. Senza questo handler finiva in
     * handleGeneric e diventava un 500 INTERNAL_ERROR, per giunta loggato come errore:
     * un percorso sbagliato di un client non e' un guasto del server.
     *
     * Il caso e' emerso disattivando springdoc in produzione, che rende /v3/api-docs
     * e /swagger-ui.html percorsi permitAll ma inesistenti. Sui percorsi protetti non
     * si notava, perche' la sicurezza risponde 401 prima di arrivare qui.
     */
    /**
     * 503 e non 500: la differenza e' l'azione che suggerisce a chi legge.
     *
     * Si logga a WARN e non a ERROR, con lo stack trace: un servizio a valle irraggiungibile
     * non e' un difetto di questo servizio, ma la causa va potuta vedere - "non risponde" da
     * solo non distingue una connessione rifiutata da un timeout, e sono due diagnosi diverse.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleServiceUnavailable(ServiceUnavailableException ex) {
        String sessionId = newSessionId();
        logger.warn("Servizio a valle non disponibile: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleResourceNotFound(NoResourceFoundException ex) {
        String sessionId = newSessionId();
        logger.debug("Risorsa non trovata: {}", ex.getResourcePath());
        return new ResponseEntity<>(
                ApiEnvelope.error("NOT_FOUND", "Resource not found",
                        "L'indirizzo richiesto non esiste.", sessionId),
                HttpStatus.NOT_FOUND
        );
    }

    /**
     * Percorso inesistente, quando NON esiste un gestore di risorse statiche.
     *
     * E' il gemello di handleResourceNotFound, e serve perche' Spring sceglie l'una o
     * l'altra eccezione a seconda della configurazione: con le risorse statiche attive un
     * percorso ignoto finisce sul loro gestore e produce NoResourceFoundException; senza -
     * e prenotazione-service e' senza, da quando la SPA e' stata rimossa - arriva qui come
     * NoHandlerFoundException.
     *
     * Mancava, e la conseguenza era che quel servizio rispondeva 500 "errore interno" a
     * qualunque indirizzo sbagliato, invece di 404. Un difetto emerso interrogando i tre
     * servizi e notando che uno rispondeva diversamente dagli altri due.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleNoHandler(NoHandlerFoundException ex) {
        String sessionId = newSessionId();
        logger.debug("Nessun gestore per {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return new ResponseEntity<>(
                ApiEnvelope.error("NOT_FOUND", "Resource not found",
                        "L'indirizzo richiesto non esiste.", sessionId),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleGeneric(Exception ex) {
        String sessionId = newSessionId();
        logger.error("Errore interno non gestito", ex);
        return new ResponseEntity<>(
                ApiEnvelope.error("INTERNAL_ERROR", "Unhandled internal error",
                        "Si e' verificato un errore imprevisto. Se il problema persiste, contatta il supporto tecnico.", sessionId),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
