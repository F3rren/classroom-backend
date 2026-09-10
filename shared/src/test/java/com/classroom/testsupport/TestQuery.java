package com.classroom.testsupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Building a query string for a test request.
 *
 * It exists because several controllers moved their request body from JSON to
 * {@code @ModelAttribute} - one field, one query parameter - so that Swagger UI can offer real
 * fillable inputs instead of a JSON box to type by hand. The integration tests exercising
 * those endpoints have to send the same shape a browser or curl would, which is this, not a
 * JSON body: a request the controller would silently ignore, binding every field to null and
 * failing its own presence validation instead of the deliberate outcome the test means to
 * check (a duplicate name, a missing id, and so on).
 *
 * A null value is left out entirely rather than encoded as the literal string "null": several
 * of the DTOs involved (BookingRequest.courseId, UpdateUserRequest.password,
 * DeleteReasonRequest.reason) treat an ABSENT field as "not provided" and a present-but-empty
 * one differently, and the distinction is exactly what some of those tests check.
 */
public final class TestQuery {

    private TestQuery() {
    }

    /** {@code ?key1=value1&key2=value2}, values URL-encoded, null values omitted. */
    public static String of(Map<String, ?> params) {
        String query = params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> encode(e.getKey()) + "=" + encode(String.valueOf(e.getValue())))
                .collect(Collectors.joining("&"));
        return query.isEmpty() ? "" : "?" + query;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
