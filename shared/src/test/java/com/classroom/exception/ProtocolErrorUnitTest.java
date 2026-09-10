package com.classroom.exception;

import com.classroom.dto.ApiEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalogue is keyed by status, so the two things worth pinning are that every entry
 * really answers for its own status, and that a status nobody listed still lands on the
 * right side of the client/server line.
 */
class ProtocolErrorUnitTest {

    @Test
    void everyEntryAnswersForItsOwnStatus() {
        for (ProtocolError error : ProtocolError.values()) {
            // Round trip: the constant found for a status has to be the constant that
            // declares it. A duplicated status would make one of the two unreachable, and
            // nothing else in the code would say so.
            HttpStatusCode declared = statusOf(error);
            assertThat(ProtocolError.of(declared))
                    .as("ProtocolError.of(%s)", declared)
                    .isEqualTo(error);
        }
    }

    @Test
    void everyEntryCarriesAllThreePieces() {
        for (ProtocolError error : ProtocolError.values()) {
            assertThat(error.code()).as("code of %s", error).isNotBlank();
            assertThat(error.message()).as("technical message of %s", error).isNotBlank();
            assertThat(error.userMessage()).as("user message of %s", error).isNotBlank();
        }
    }

    @Test
    void anUnlistedClientErrorStaysAClientError() {
        // ErrorResponseException carries a status of its own choosing, so this is reachable.
        // 418 is not in the catalogue: answering INTERNAL_ERROR would blame the server for
        // the caller's request.
        assertThat(ProtocolError.of(HttpStatusCode.valueOf(418)))
                .isEqualTo(ProtocolError.BAD_REQUEST);
    }

    @Test
    void anUnlistedServerErrorStaysAServerError() {
        assertThat(ProtocolError.of(HttpStatusCode.valueOf(507)))
                .isEqualTo(ProtocolError.INTERNAL_ERROR);
    }

    @Test
    void aStatusOutsideTheHttpStatusEnumIsStillMatchedByValue() {
        // A non-standard code arrives as a DefaultHttpStatusCode, which is never equal() to
        // an HttpStatus constant. Comparing by value() is what makes this work, and this
        // test is what keeps somebody from "simplifying" it back to equals().
        assertThat(ProtocolError.of(HttpStatusCode.valueOf(404)))
                .isEqualTo(ProtocolError.NOT_FOUND);
    }

    @Test
    void theEnvelopeCarriesTheThreePiecesAndTheSessionId() {
        ApiEnvelope<Void> envelope = ProtocolError.METHOD_NOT_ALLOWED.toEnvelope("REQ_TEST0001");

        assertThat(envelope.isSuccess()).isFalse();
        assertThat(envelope.getError()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(envelope.getMessage()).isEqualTo(ProtocolError.METHOD_NOT_ALLOWED.message());
        assertThat(envelope.getUserMessage()).isEqualTo(ProtocolError.METHOD_NOT_ALLOWED.userMessage());
        assertThat(envelope.getSessionId()).isEqualTo("REQ_TEST0001");
    }

    @Test
    void the404KeepsTheWordingTheHandWrittenHandlersUsed() {
        // Two handlers used to answer this by hand, and a client may already be branching on
        // the code. The migration to the base class must not have changed either field.
        assertThat(ProtocolError.NOT_FOUND.code()).isEqualTo("NOT_FOUND");
        assertThat(ProtocolError.NOT_FOUND.message()).isEqualTo("Resource not found");
        assertThat(ProtocolError.NOT_FOUND.userMessage()).isEqualTo("L'indirizzo richiesto non esiste.");
    }

    @Test
    void the500KeepsTheWordingHandleGenericUsed() {
        assertThat(ProtocolError.INTERNAL_ERROR.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(ProtocolError.INTERNAL_ERROR.message()).isEqualTo("Unhandled internal error");
        assertThat(ProtocolError.INTERNAL_ERROR.userMessage())
                .isEqualTo("Si e' verificato un errore imprevisto. Se il problema persiste, contatta il supporto tecnico.");
    }

    /**
     * The catalogue does not expose its status - nothing in production needs it, since the
     * status always arrives from the caller. The test does need it, and reads it back
     * through of(), which is the only contract there is.
     */
    private static HttpStatusCode statusOf(ProtocolError error) {
        for (HttpStatus status : HttpStatus.values()) {
            if (ProtocolError.of(status) == error && matchesExactly(status, error)) {
                return status;
            }
        }
        throw new AssertionError("no status maps to " + error);
    }

    /** Tells a real entry apart from the two that also serve as fallbacks. */
    private static boolean matchesExactly(HttpStatus status, ProtocolError error) {
        return error.name().equals(status.name())
                || (error == ProtocolError.INTERNAL_ERROR && status == HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
