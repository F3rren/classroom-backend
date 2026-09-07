package com.classroom.auth.client;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The calls that delete a user's data in the other services.
 *
 * The point of these tests is to count the requests that actually go out, not just the
 * outcome: the difference between "retries" and "does not retry" is invisible in the return
 * value, and it is exactly what decides whether a transient failure leaves the operation
 * half done.
 *
 * The distinction to hold still: it retries on a transport failure or a 5xx, because the
 * second attempt often works; it does NOT retry on a 4xx, because there the downstream
 * service is refusing, and repeating would give the same outcome while only delaying the
 * answer.
 */
class UserDataClientUnitTest {

    private static final String NOTIFICATIONS = "http://notifiche.test";
    private static final String BOOKINGS = "http://prenotazioni.test";
    private static final String URI_NOTIFICATIONS = NOTIFICATIONS + "/api/notifications/internal/user/7";
    private static final String URI_BOOKINGS = BOOKINGS + "/api/bookings/internal/user/7";

    private RestClient.Builder builder;
    private MockRestServiceServer fakeService;
    private UserDataClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        fakeService = MockRestServiceServer.bindTo(builder).build();
        // The current request is only there to forward the Authorization header: there is no
        // HTTP request in flight here, and a mock answering null is perfectly fine.
        client = new UserDataClient(builder, NOTIFICATIONS, BOOKINGS, mock(HttpServletRequest.class));
    }

    @Test
    void nothingIsLeftBehindWhenAllGoesWell() {
        fakeService.expect(requestTo(URI_NOTIFICATIONS)).andRespond(withSuccess());
        fakeService.expect(requestTo(URI_BOOKINGS)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).isEmpty();
        fakeService.verify();
    }

    @Test
    void aTransientFailureIsOvercomeByRetrying() {
        // THE reason for the retries. A restarting service fails the first attempt and
        // answers the second: that alone used to be enough to leave the deletion half done,
        // and finishing it depended on a person noticing.
        fakeService.expect(requestTo(URI_NOTIFICATIONS)).andRespond(withServerError());
        fakeService.expect(requestTo(URI_NOTIFICATIONS)).andRespond(withSuccess());
        fakeService.expect(requestTo(URI_BOOKINGS)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).isEmpty();
        fakeService.verify();
    }

    @Test
    void itGivesUpAfterThreeAttempts() {
        // The retries are not endless: a service that really is down must not leave the
        // administrator's request hanging.
        fakeService.expect(ExpectedCount.times(3), requestTo(URI_NOTIFICATIONS)).andRespond(withServerError());
        fakeService.expect(requestTo(URI_BOOKINGS)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).containsExactly("notifications");
        fakeService.verify();
    }

    @Test
    void aRefusalFromTheDownstreamServiceIsNotRetried() {
        // A 4xx is an answer, not a failure: repeating it would give the same outcome. The
        // exact count is the only thing telling this case from the previous one - the
        // outcome is identical, the behaviour is not.
        fakeService.expect(ExpectedCount.once(), requestTo(URI_NOTIFICATIONS))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        fakeService.expect(requestTo(URI_BOOKINGS)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).containsExactly("notifications");
        fakeService.verify();
    }

    @Test
    void theSecondServiceIsCalledEvenWhenTheFirstFails() {
        // Stopping at the first error would leave more behind without telling the reader of
        // the message any more: both are attempted, and the error names both.
        fakeService.expect(ExpectedCount.once(), requestTo(URI_NOTIFICATIONS))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        fakeService.expect(ExpectedCount.once(), requestTo(URI_BOOKINGS))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        List<String> failed = client.deleteDataOf(7L);

        assertThat(failed).containsExactly("notifications", "bookings");
        fakeService.verify();
    }

    @Test
    void everyCallCarriesTheCorrelationId() {
        // Without it, an operation crossing three services ends up in the logs under three
        // different keys: correlation would work everywhere except where it is needed.
        fakeService.expect(requestTo(URI_NOTIFICATIONS))
                .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("X-Request-Id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
                .andRespond(withSuccess());
        fakeService.expect(requestTo(URI_BOOKINGS)).andRespond(withSuccess());

        assertThat(client.deleteDataOf(7L)).isEmpty();
        fakeService.verify();
    }
}
