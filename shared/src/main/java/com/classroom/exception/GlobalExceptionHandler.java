package com.classroom.exception;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.dto.ApiEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


/**
 * The single place where controller errors are turned into responses. It replaced the
 * private createErrorResponse/generateSessionId methods that were duplicated across four
 * controllers.
 *
 * It covers only the exceptions raised INSIDE the controller method. Refusals at the
 * security-filter level (no token, invalid token) are handled separately by
 * ApiAuthenticationEntryPoint and ApiAccessDeniedHandler, because they happen before the
 * dispatch to the controller - and therefore before this @RestControllerAdvice - takes place.
 *
 * WHY IT EXTENDS ResponseEntityExceptionHandler. Because handleGeneric below is mapped on
 * Exception, and ExceptionHandlerExceptionResolver is consulted BEFORE Spring's own
 * DefaultHandlerExceptionResolver: every exception Spring MVC raises for a malformed
 * request was being caught here and answered 500, although each of them already carries the
 * right status. Measured on booking-service before this change, all of these answered
 * 500 INTERNAL_ERROR: a body that is not JSON, a body with a field of the wrong type,
 * /api/rooms/abc, a missing query parameter, a method that does not exist on that path, and
 * a wrong Content-Type. A request with an Accept the service cannot produce was worse still:
 * the envelope built for it was itself unwritable, the second failure escaped to the
 * container's error dispatch, and the client got a 401 telling it to log in.
 *
 * Two cases of that same family had already been noticed and patched one at a time -
 * NoResourceFoundException and NoHandlerFoundException, each with a handler of its own. The
 * base class covers those two and about eighteen more, sets the headers the RFCs require
 * (Allow on a 405, Accept on a 415), and logs none of it as an application error. What it
 * does NOT do is answer in this project's envelope, and that is the only thing overridden
 * below.
 *
 * The consequence for the handlers in this class: an @ExceptionHandler for a type the base
 * class already maps is not an override, it is an AMBIGUITY, and the context refuses to
 * start. Validation and the two 404s are therefore expressed as overrides. The domain
 * exceptions below are not in the base class's list, so they stay as they were.
 *
 * Every userMessage here stays Italian: it is the one field of the envelope a person reads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Hides the commons-logging "logger" the base class declares. Deliberate: every class in
    // this codebase logs through SLF4J under this name, and the inherited field is never
    // read by the base class itself.
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** What is said when a rejected value carries no message of its own. */
    private static final String GENERIC_VALIDATION_MESSAGE = "I dati inviati non sono validi.";

    /**
     * The id of the request in flight, not a new one.
     *
     * This method used to generate one of its own, so the same request appeared in the logs
     * under two different ids: the controller's, and the one produced here while answering.
     * They were unrelated, and there was no way to tell they belonged to the same call.
     */
    private String currentSessionId() {
        return RequestCorrelationFilter.current();
    }

    // ==================== the framework's own exceptions ====================

    /**
     * The one point where everything the base class handles is given this project's shape.
     *
     * The headers arrive already filled in by the base class - Allow on a 405, Accept on a
     * 415 - and are passed through untouched: they are what makes those two statuses useful
     * rather than merely correct. Only the body changes, from Spring's ProblemDetail to the
     * envelope every other response in the system uses.
     *
     * A body that is already an ApiEnvelope is left alone: that is how the overrides below
     * keep their own error code instead of receiving the generic one for their status. The
     * one case that does not pass through here at all is the 406, for the reason its own
     * override explains.
     *
     * The exception's own message is NOT copied into the response. It can name internal
     * classes and fields (Jackson's do), and the envelope's technical message has never
     * carried that kind of detail - handleDataIntegrityViolation made the same choice. The
     * detail goes to the log, where the sessionId ties it back to this exact response.
     */
    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex, @Nullable Object body, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {

        Object envelope = body instanceof ApiEnvelope
                ? body
                : ProtocolError.of(statusCode).toEnvelope(currentSessionId());

        logByStatus(statusCode, ex);
        return super.handleExceptionInternal(ex, envelope, headers, statusCode, request);
    }

    /**
     * The level says who has to do something, which is the rule the whole codebase logs by.
     *
     * A 404 stays at DEBUG because a path looked up and not found is a normal outcome of
     * using the application; the other 4xx are refusals that say something about the caller,
     * so WARN, at the same level as the refusals the domain raises. A 5xx from here is a
     * genuine defect of ours - a missing path variable, a response that cannot be
     * serialised - and keeps the stack trace.
     */
    private void logByStatus(HttpStatusCode status, Exception ex) {
        if (status.is5xxServerError()) {
            logger.error("Request failed with {}", status.value(), ex);
        } else if (status.value() == HttpStatus.NOT_FOUND.value()) {
            logger.debug("Nothing mapped for the requested path: {}", ex.getMessage());
        } else {
            logger.warn("Request refused with {}: {}", status.value(), ex.getMessage());
        }
    }

    /**
     * The one status that has to be answered with NO body at all.
     *
     * A client that accepts nothing this service can produce cannot be sent the envelope
     * either: attempting it fails a second time, this time while writing the response, and
     * that second failure escapes to the container's error dispatch - which re-enters the
     * security chain on /error with no authentication and answers 401. Measured: before this
     * override, a request carrying an Accept the service cannot satisfy came back as a 401
     * telling the caller to log in, with two stack traces at ERROR behind it.
     *
     * So the correct answer here is the bare status, which is also what the RFC intends: a
     * 406 says "I cannot represent this the way you asked", and a body would have to be in
     * exactly the representation just refused.
     *
     * It does not go through handleExceptionInternal because the base class's version fills
     * a null body back in with a ProblemDetail, which would land us in the same place.
     */
    @Override
    @Nullable
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            @NonNull HttpMediaTypeNotAcceptableException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {

        logByStatus(status, ex);
        return new ResponseEntity<>(headers, status);
    }

    /**
     * Bean Validation rejected the body.
     *
     * An override and no longer an @ExceptionHandler, because the base class maps this type
     * too and two mappings for one exception stop the context from starting. The behaviour
     * is unchanged: the code stays VALIDATION_ERROR and the userMessage stays the message of
     * the FIRST field error, which is the one thing the generic 400 could not reproduce -
     * it is the per-field Italian sentence written on the DTO.
     */
    @Override
    @Nullable
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {

        FieldError firstError = ex.getBindingResult().getFieldError();
        String userMessage = firstError != null
                ? firstError.getDefaultMessage()
                : GENERIC_VALIDATION_MESSAGE;
        return handleExceptionInternal(ex,
                ApiEnvelope.error("VALIDATION_ERROR", "Request body failed validation",
                        userMessage, currentSessionId()),
                headers, status, request);
    }

    /**
     * A constraint on a method parameter said no: @Positive on a path variable, say.
     *
     * The twin of the override above, for the other half of Bean Validation. It matters
     * because it is what lets a rule like "an id is a positive number" be written once, on
     * the parameter it belongs to, instead of as an if at the top of every method that takes
     * one - which is how it used to be written, in eight copies across three controllers and
     * in three different Italian wordings of the same sentence.
     *
     * The code stays VALIDATION_ERROR, the same as for a rejected body: from the caller's
     * side the two are one thing, "what you sent was not acceptable, the reason is in
     * userMessage". The sentence is the message declared on the annotation, so it is written
     * next to the rule it explains.
     *
     * Spring 6.1 raises this only when the controller class is NOT annotated @Validated;
     * with that annotation the AOP-based validation runs instead and throws
     * ConstraintViolationException, which nothing here maps. Do not add it.
     */
    @Override
    @Nullable
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {

        String userMessage = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(GENERIC_VALIDATION_MESSAGE);
        return handleExceptionInternal(ex,
                ApiEnvelope.error("VALIDATION_ERROR", "Request parameters failed validation",
                        userMessage, currentSessionId()),
                headers, status, request);
    }

    // ==================== the domain's exceptions ====================
    //
    // None of the types below is in the base class's list, so they stay ordinary
    // @ExceptionHandler methods and keep answering exactly as before.

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAccessDenied(AccessDeniedException ex) {
        String sessionId = currentSessionId();
        // If the message was built explicitly by an application throw, it is preserved;
        // otherwise (a refusal generated by @PreAuthorize, say) a message equivalent to the
        // one already in use for denied admin access is applied.
        String userMessage = ex.getMessage() != null
                ? ex.getMessage()
                : "Accesso negato: privilegi insufficienti per questa operazione.";
        logger.warn("Access denied: {}", userMessage);
        return new ResponseEntity<>(
                ApiEnvelope.error("ACCESS_DENIED", "Access denied", userMessage, sessionId),
                HttpStatus.FORBIDDEN
        );
    }

    /**
     * Input the domain will not accept, which Bean Validation could not have caught, typically
     * because it depends on the domain. IllegalArgumentException is deliberately NOT mapped
     * here: that one signals a programming error, and turning it into a 400 would pass the
     * server's bugs off as the caller's fault.
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleInvalidRequest(InvalidRequestException ex) {
        String sessionId = currentSessionId();
        // WARN and not DEBUG: it is a 400, like the validation above, and for the same
        // reason - a body that does not honour the contract says something about the caller.
        // They used to sit at two different levels for no reason.
        logger.warn("Invalid request: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.BAD_REQUEST
        );
    }

    /**
     * A resource that does not exist. This case used to arrive here as a null, and every
     * controller decided for itself that it meant a 404 and with what message: sixteen
     * scattered "== null" checks, each with its own hand-written sentence.
     *
     * Not to be confused with a path that does not exist, which is the framework's
     * NoResourceFoundException / NoHandlerFoundException and is now answered by the base
     * class, with ProtocolError.NOT_FOUND supplying the same code and sentence as before.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        String sessionId = currentSessionId();
        // debug and not warn: a resource looked up and not found is a normal outcome of using
        // the application, not a symptom of anything being wrong.
        logger.debug("Resource not found: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.NOT_FOUND
        );
    }

    /**
     * A conflict with the current state of the data. This also catches BookingConflictException,
     * which is a subtype: the more specific handler below is still preferred by Spring, and it
     * exists to tell the case apart in the logs.
     */
    @ExceptionHandler(DomainConflictException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDomainConflict(DomainConflictException ex) {
        String sessionId = currentSessionId();
        logger.warn("Domain conflict: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleBookingConflict(BookingConflictException ex) {
        String sessionId = currentSessionId();
        logger.warn("Booking conflict: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String sessionId = currentSessionId();
        logger.warn("Database constraint violated: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiEnvelope.error("CONFLICT", "Conflict with the current state of the data",
                        "L'operazione non e' andata a buon fine per un conflitto con dati esistenti.", sessionId),
                HttpStatus.CONFLICT
        );
    }

    /**
     * 503 and not 500: the difference is the action it suggests to whoever reads it.
     *
     * Logged at WARN rather than ERROR, with the stack trace: an unreachable downstream
     * service is not a defect of this service, but the cause has to be visible - "not
     * answering" on its own does not separate a refused connection from a timeout, and those
     * are two different diagnoses.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleServiceUnavailable(ServiceUnavailableException ex) {
        String sessionId = currentSessionId();
        logger.warn("Downstream service unavailable: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(
                ApiEnvelope.error(ex.getErrorCode(), ex.getMessage(), ex.getUserMessage(), sessionId),
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    /**
     * The genuinely unknown, and from now on only that.
     *
     * It stays mapped on Exception, but the base class's own handler is more specific for
     * every type Spring MVC raises, so those no longer land here. What is left is what this
     * handler was always for: a defect nobody anticipated, with the stack trace to find it.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleGeneric(Exception ex) {
        logger.error("Unhandled internal error", ex);
        return new ResponseEntity<>(
                ProtocolError.INTERNAL_ERROR.toEnvelope(currentSessionId()),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
