package com.classroom.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Reading the JSON out of an HTTP response in a test.
 *
 * It already existed, eleven times over: every integration test class built its own
 * ObjectMapper e ci avvolgeva intorno un `private Map&lt;String, Object&gt; asMap(String)`
 * identical to the other ten. Eleven copies of the same line are eleven places to fix on the
 * day one more case has to be handled.
 *
 * A single static ObjectMapper is fine: it is designed to be shared and is thread-safe once
 * configured.
 *
 * It lives here rather than in each module because the three services already depend on
 * shared's test-jar - that is how they use {@link TestJwt}.
 */
public final class TestJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestJson() {
    }

    /**
     * The JSON as a map.
     *
     * It rethrows as a RuntimeException on purpose: in a test, unreadable JSON is not a case
     * to handle - the test should fail immediately, saying what actually arrived.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("JSON not readable: " + json, e);
        }
    }

    /** The body of a response, already as a map. */
    public static Map<String, Object> bodyOf(ResponseEntity<String> response) {
        return asMap(response.getBody());
    }
}
