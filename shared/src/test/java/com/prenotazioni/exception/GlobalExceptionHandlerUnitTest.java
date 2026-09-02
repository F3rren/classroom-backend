package com.prenotazioni.exception;

import com.prenotazioni.dto.ApiEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gli handler di conflitto e di errore interno non sono raggiungibili dai test HTTP:
 * su H2 il vincolo anti-sovrapposizione non esiste, quindi nessuna richiesta puo'
 * produrre una DataIntegrityViolationException o una BookingConflictException.
 * Qui l'handler viene istanziato direttamente.
 */
class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Metodo fittizio usato solo per costruire un MethodParameter valido. */
    @SuppressWarnings("unused")
    private void metodoDiComodo(String argomento) {
    }

    /**
     * BeanPropertyBindingResult richiede che il campo respinto esista davvero sul target,
     * altrimenti rejectValue produce un errore globale e getFieldError() torna null.
     * Prima si usava AulaRequest, che vive nel modulo applicativo: qui basta un bean locale.
     */
    static class OggettoConCapienza {
        private Integer capienza;

        public Integer getCapienza() {
            return capienza;
        }

        public void setCapienza(Integer capienza) {
            this.capienza = capienza;
        }
    }

    @Test
    void bookingConflictBecomes409WithItsOwnErrorCode() {
        BookingConflictException ex = new BookingConflictException(
                "UPDATE_CONFLICT", "Impossibile modificare", "Riprova con un altro orario.");

        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleBookingConflict(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getError()).isEqualTo("UPDATE_CONFLICT");
        assertThat(resp.getBody().getUserMessage()).isEqualTo("Riprova con un altro orario.");
        assertThat(resp.getBody().isSuccess()).isFalse();
    }

    @Test
    void dataIntegrityViolationBecomesGeneric409() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("vincolo violato"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getError()).isEqualTo("CONFLICT");
    }

    @Test
    void missingResourceStays404AndDoesNotBecomeAnInternalError() {
        // Regressione: senza un handler dedicato, NoResourceFoundException finiva in
        // handleGeneric e un percorso inesistente rispondeva 500 INTERNAL_ERROR.
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleResourceNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/v3/api-docs"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getError()).isEqualTo("NOT_FOUND");
    }

    @Test
    void unexpectedExceptionBecomes500WithoutLeakingDetails() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleGeneric(
                new IllegalStateException("dettaglio interno che non deve uscire"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getError()).isEqualTo("INTERNAL_ERROR");
        // il messaggio tecnico non deve finire nella risposta
        assertThat(resp.getBody().getUserMessage()).doesNotContain("dettaglio interno");
    }

    @Test
    void accessDeniedKeepsAnExplicitMessage() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleAccessDenied(
                new AccessDeniedException("Puoi vedere solo le tue prenotazioni."));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getUserMessage()).isEqualTo("Puoi vedere solo le tue prenotazioni.");
    }

    @Test
    void validationWithoutFieldErrorsFallsBackToGenericMessage() throws Exception {
        // ramo firstError == null: un BindingResult senza errori di campo
        Method metodo = getClass().getDeclaredMethod("metodoDiComodo", String.class);
        MethodParameter parametro = new MethodParameter(metodo, 0);
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "oggetto");

        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleValidation(
                new MethodArgumentNotValidException(parametro, binding));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().getUserMessage()).isEqualTo("I dati inviati non sono validi.");
    }

    @Test
    void validationUsesTheFirstFieldErrorMessage() throws Exception {
        Method metodo = getClass().getDeclaredMethod("metodoDiComodo", String.class);
        MethodParameter parametro = new MethodParameter(metodo, 0);
        // il target deve avere un campo vero: rejectValue su un campo inesistente
        // produrrebbe un errore globale e getFieldError() tornerebbe null
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new OggettoConCapienza(), "oggettoConCapienza");
        binding.rejectValue("capienza", "Positive", "La capienza deve essere un numero positivo.");

        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleValidation(
                new MethodArgumentNotValidException(parametro, binding));

        assertThat(resp.getBody().getUserMessage()).isEqualTo("La capienza deve essere un numero positivo.");
    }
}
