package com.classroom.booking;

import com.classroom.testsupport.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A malformed request has to be answered with the status that says so, over real HTTP.
 *
 * Every case below was measured answering 500 INTERNAL_ERROR before GlobalExceptionHandler
 * extended ResponseEntityExceptionHandler: @ExceptionHandler(Exception.class) was consulted
 * before Spring's own resolver and flattened onto "nobody knows what happened" a set of
 * exceptions that each already carried the right status. The consequence was not only the
 * wrong status: each one was logged at ERROR with a stack trace, in a codebase whose rule is
 * that an ERROR in production is a fact and not noise.
 *
 * It is an HTTP test and not a unit test on purpose. The unit test next to the handler
 * pins the mapping; only a real request proves the wiring - above all that the advice still
 * loads, since an @ExceptionHandler for a type the base class already maps is an ambiguity
 * that stops the context from starting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProtocolErrorsTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders authenticated() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestJwt.forAdmin(1L, "admin@test.it"));
        return headers;
    }

    private ResponseEntity<String> call(HttpMethod method, String path, HttpEntity<String> request) {
        return rest.exchange("http://localhost:" + port + path, method, request, String.class);
    }

    /** Every error, whatever raised it, keeps the one envelope the whole API answers with. */
    private void assertIsTheUsualEnvelope(ResponseEntity<String> response, String expectedCode) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody())
                .contains("\"success\":false")
                .contains("\"error\":\"" + expectedCode + "\"")
                .contains("\"userMessage\"")
                .contains("\"sessionId\"");
    }

    @Test
    void aBodyThatIsNotJsonBecomes400() {
        HttpHeaders headers = authenticated();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = call(HttpMethod.POST, "/api/bookings/book",
                new HttpEntity<>("{ this is not json", headers));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertIsTheUsualEnvelope(response, "BAD_REQUEST");
    }

    @Test
    void aFieldOfTheWrongTypeInTheBodyBecomes400() {
        HttpHeaders headers = authenticated();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = call(HttpMethod.POST, "/api/bookings/book",
                new HttpEntity<>("{\"roomId\":\"not-a-number\"}", headers));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertIsTheUsualEnvelope(response, "BAD_REQUEST");
    }

    @Test
    void aPathVariableThatIsNotANumberBecomes400() {
        ResponseEntity<String> response = call(HttpMethod.GET, "/api/rooms/abc",
                new HttpEntity<>(authenticated()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertIsTheUsualEnvelope(response, "BAD_REQUEST");
    }

    @Test
    void aMissingQueryParameterBecomes400() {
        // /availability requires roomId, start and end: none is supplied here.
        ResponseEntity<String> response = call(HttpMethod.GET, "/api/bookings/availability",
                new HttpEntity<>(authenticated()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertIsTheUsualEnvelope(response, "BAD_REQUEST");
    }

    @Test
    void aMethodThatDoesNotExistOnThePathBecomes405AndSaysWhichOnesDo() {
        ResponseEntity<String> response = call(HttpMethod.DELETE, "/api/rooms/stats",
                new HttpEntity<>(authenticated()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertIsTheUsualEnvelope(response, "METHOD_NOT_ALLOWED");
        // RFC 9110 requires it, and it is the only way the caller learns what to use instead.
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
    }

    @Test
    void aContentTypeNobodyReadsBecomes415() {
        HttpHeaders headers = authenticated();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<String> response = call(HttpMethod.POST, "/api/bookings/book",
                new HttpEntity<>("plain text", headers));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertIsTheUsualEnvelope(response, "UNSUPPORTED_MEDIA_TYPE");
        assertThat(response.getHeaders().getAccept()).contains(MediaType.APPLICATION_JSON);
    }

    @Test
    void anUnknownPathStays404() {
        // Already correct before the change, thanks to a handler written by hand for
        // NoHandlerFoundException. That handler is gone - the base class covers the type,
        // and a second mapping for it would stop the context from starting - so what needs
        // pinning is that the answer did not move with it.
        ResponseEntity<String> response = call(HttpMethod.GET, "/api/does-not-exist",
                new HttpEntity<>(authenticated()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertIsTheUsualEnvelope(response, "NOT_FOUND");
    }

    @Test
    void theRequestIdInTheBodyIsTheOneInTheHeader() {
        ResponseEntity<String> response = call(HttpMethod.GET, "/api/rooms/abc",
                new HttpEntity<>(authenticated()));

        String header = response.getHeaders().getFirst("X-Request-Id");
        assertThat(header).isNotBlank();
        // The whole point of the correlation id: one request, one id, in the log, in the
        // header and in the body. An error answered by the framework is no exception.
        assertThat(response.getBody()).contains("\"sessionId\":\"" + header + "\"");
    }

    @Test
    void aRefusalFromTheSecurityChainCarriesTheSameIdAsItsHeader() {
        // Measured before the fix: the header said REQ_852A1225 and the body said
        // AUTH_98C52C23 - two ids for one request. The two security handlers answer from
        // inside the filter chain, before any controller, and used to mint an id of their
        // own rather than read the one the correlation filter had already put on the
        // request.
        ResponseEntity<String> response = call(HttpMethod.GET, "/api/rooms",
                new HttpEntity<>(new HttpHeaders()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String header = response.getHeaders().getFirst("X-Request-Id");
        assertThat(header).isNotBlank();
        assertThat(response.getBody()).contains("\"sessionId\":\"" + header + "\"");
    }

    @Test
    void a401SaysWhatAuthenticationToAttempt() {
        // RFC 9110 section 11.6.1 makes WWW-Authenticate mandatory on a 401. It was absent.
        ResponseEntity<String> noToken = call(HttpMethod.GET, "/api/rooms",
                new HttpEntity<>(new HttpHeaders()));

        assertThat(noToken.getHeaders().getFirst("WWW-Authenticate"))
                .isEqualTo("Bearer realm=\"classroom\"");

        HttpHeaders expired = new HttpHeaders();
        expired.setBearerAuth(TestJwt.expired(1L, "scaduto@test.it"));
        ResponseEntity<String> withBadToken = call(HttpMethod.GET, "/api/rooms",
                new HttpEntity<>(expired));

        // RFC 6750 section 3.1: a token that was sent and refused is a different problem
        // from no token at all, and calls for a different move by the client.
        assertThat(withBadToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(withBadToken.getHeaders().getFirst("WWW-Authenticate"))
                .contains("error=\"invalid_token\"");
    }

    @Test
    void anIdThatIsNotPositiveBecomes400WithTheSameSentenceEverywhere() {
        // The rule used to be an if at the top of each method: eight copies across three
        // controllers, in three different Italian wordings of the identical sentence. It is
        // now @Positive on the parameter, so there is one wording, and it is written next to
        // the rule it explains.
        List<String> everyEndpointTakingARoomId = List.of(
                "/api/rooms/0", "/api/rooms/-1/detailed", "/api/admin/rooms/0");

        for (String path : everyEndpointTakingARoomId) {
            ResponseEntity<String> response = call(HttpMethod.GET, path,
                    new HttpEntity<>(authenticated()));

            assertThat(response.getStatusCode()).as("status of %s", path).isEqualTo(HttpStatus.BAD_REQUEST);
            assertIsTheUsualEnvelope(response, "VALIDATION_ERROR");
            assertThat(response.getBody()).as("message of %s", path)
                    .contains("L'ID dell'aula deve essere un numero positivo.");
        }
    }

    @Test
    void aNonPositiveIdIsRefusedBeforeTheServiceIsAskedAnything() {
        // 400 and not 404: id 0 is not a room that might exist, it is a request that cannot
        // be honoured. Telling the two apart is the same distinction the domain already
        // makes between InvalidRequestException and ResourceNotFoundException.
        ResponseEntity<String> response = call(HttpMethod.DELETE, "/api/admin/bookings/-5",
                new HttpEntity<>(authenticated()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertIsTheUsualEnvelope(response, "VALIDATION_ERROR");
        assertThat(response.getBody()).contains("L'ID della prenotazione deve essere un numero positivo.");
    }

    @Test
    void aPathVariableThatIsNotANumberIsADifferentCaseFromOneThatIsNotPositive() {
        // Both are 400, and they are told apart on purpose: a value that cannot be converted
        // never reaches the constraint, so it carries the framework's code, while a value
        // that converts and then breaks the rule carries the rule's own sentence. Spring
        // makes the same split, and a client reading userMessage gets the useful half either
        // way.
        ResponseEntity<String> notANumber = call(HttpMethod.GET, "/api/rooms/abc",
                new HttpEntity<>(authenticated()));
        ResponseEntity<String> notPositive = call(HttpMethod.GET, "/api/rooms/0",
                new HttpEntity<>(authenticated()));

        assertThat(notANumber.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notPositive.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertIsTheUsualEnvelope(notANumber, "BAD_REQUEST");
        assertIsTheUsualEnvelope(notPositive, "VALIDATION_ERROR");
    }

    @Test
    void anAcceptThatExcludesJsonBecomes406() {
        HttpHeaders headers = authenticated();
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.custom+xml")));

        ResponseEntity<String> response = call(HttpMethod.GET, "/api/rooms",
                new HttpEntity<>(headers));

        // No body is asserted, and that is not an oversight: a client that accepts nothing
        // this service can produce cannot be sent an envelope either, so the correct answer
        // is the bare status. Before the change this case did not even reach 406 - the
        // envelope built for it was itself unwritable, the second failure escaped to the
        // container's error dispatch, and the caller received a 401 telling it to log in.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }
}
