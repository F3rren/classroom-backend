package com.classroom.auth.client;

import java.util.List;
import java.util.ArrayList;
import org.springframework.web.client.HttpClientErrorException;
import com.classroom.config.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Deletes what belongs to a user but lives in other services.
 *
 * While everything sat in one database, UserService deleted notifications, bookings and the
 * user inside a single transaction, and the foreign keys guaranteed nothing was left
 * orphaned. Those rows now live in two databases this service cannot touch, so the deletion
 * becomes a sequence of calls:
 *
 *   - it is NOT atomic. If one fails, the user can disappear leaving their bookings behind.
 *     The window is not theoretical.
 *   - that is why every failure is logged as ERROR and not as a warning: it needs cleaning
 *     up, not consoling.
 *   - the order is deliberate: dependent data first, the user last. The other way round, an
 *     error after removing the user would leave rows whose owner nobody can name any more.
 *
 * The token of the admin who asked for the deletion is forwarded as it is: the downstream
 * services verify it themselves and require the ADMIN role, so there is no privileged
 * service-to-service lane to protect separately.
 */
@Component
public class UserDataClient {

    private static final Logger logger = LoggerFactory.getLogger(UserDataClient.class);

    /** Three attempts: the first, plus two for failures that last less than a second. */
    private static final int ATTEMPTS = 3;

    /** Grows each round (0.2s, 0.4s): a restarting service does not come back instantly. */
    private static final long INITIAL_BACKOFF_MS = 200;

    private final RestClient notifications;
    private final RestClient bookings;
    private final HttpServletRequest currentRequest;

    UserDataClient(RestClient.Builder builder,
                     @Value("${classroom.notification-service.url:http://localhost:17104}") String notificationsUrl,
                     @Value("${classroom.booking-service.url:http://localhost:17103}") String bookingsUrl,
                     HttpServletRequest currentRequest) {
        this.notifications = builder.clone().baseUrl(notificationsUrl).build();
        this.bookings = builder.clone().baseUrl(bookingsUrl).build();
        this.currentRequest = currentRequest;
    }

    /**
     * Deletes the user's data in the other services.
     *
     * @return the names of the data that could NOT be deleted, empty if everything went
     *         through. A boolean was not enough: the caller has to be able to say in the
     *         error message what was left behind, otherwise the only information is
     *         "something failed" and whoever retries does not know what to expect.
     */
    public List<String> deleteDataOf(Long userId) {
        List<String> failed = new ArrayList<>();
        // Both calls are attempted even if the first fails: stopping would leave more behind
        // without telling the reader of the error anything more.
        if (!delete(notifications, "/api/notifications/internal/user/{id}", userId, "notifications")) {
            failed.add("notifications");
        }
        if (!delete(bookings, "/api/bookings/internal/user/{id}", userId, "bookings")) {
            failed.add("bookings");
        }
        return failed;
    }

    /**
     * A DELETE with a few attempts, because most failures here are transient.
     *
     * A service that is restarting, a connection refused for an instant, a momentary 5xx:
     * they fail on the first try and often not on the second. Without retries each of these
     * left the operation half done, and its completion depended on a human noticing and
     * repeating it.
     *
     * It does NOT retry on a 4xx: there the downstream service is refusing the request, and
     * repeating it would give the same outcome while only delaying the answer. The
     * distinction matters: retrying what cannot succeed is how a clear error turns into a
     * timeout.
     *
     * DELETEs are idempotent, so an attempt that actually succeeded but whose response was
     * lost does no damage on the next round.
     */
    private boolean delete(RestClient client, String uri, Long userId, String what) {
        Exception last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                client.delete()
                        .uri(uri, userId)
                        .header(HttpHeaders.AUTHORIZATION, currentAuthorization())
                        // Without this line the correlation chain breaks exactly here: the
                        // downstream services do not receive the id, generate a new one, and
                        // an operation crossing three services ends up in the logs under
                        // three different keys. That is, correlation would work everywhere
                        // except where it is needed.
                        .header(RequestCorrelationFilter.HEADER, RequestCorrelationFilter.current())
                        .retrieve()
                        .toBodilessEntity();
                if (attempt > 1) {
                    logger.info("{} of userId={} deleted on attempt {}", what, userId, attempt);
                }
                return true;
            } catch (HttpClientErrorException e) {
                logger.error("{} of userId={}: the downstream service refused the request "
                        + "({}). Not retrying: repeating it would give the same outcome.",
                        what, userId, e.getStatusCode());
                return false;
            } catch (Exception e) {
                last = e;
                if (attempt < ATTEMPTS) {
                    waitFor(INITIAL_BACKOFF_MS * attempt);
                }
            }
        }
        logger.error("{} of userId={} not deleted after {} attempts: the user is NOT removed, "
                + "so those rows still have an owner and the operation stays repeatable. "
                + "Cause: {}",
                what, userId, ATTEMPTS, last != null ? last.getMessage() : "unknown");
        return false;
    }

    private void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Restore the flag and stop retrying: whoever interrupted the thread wants it to
            // stop, not to sleep again.
            Thread.currentThread().interrupt();
        }
    }

    private String currentAuthorization() {
        String header = currentRequest.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null ? header : "";
    }
}
