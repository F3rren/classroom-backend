package com.classroom.auth;

import com.classroom.testsupport.TestJson;
import com.classroom.auth.model.User;
import com.classroom.auth.repository.UserRepository;
import com.classroom.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full lifecycle of a refresh token, over real HTTP: login hands one out, refresh
 * exchanges it for a new pair and retires the old one, and logout ends the session early.
 *
 * Same shape as AdminUsersTest/AuthValidationTest: a real POST /api/auth/login rather than a
 * hand-signed TestJwt, because what is under test here IS the token this service itself
 * issues and later has to recognise again.
 *
 * NOT the autowired TestRestTemplate for refresh()/logout(), unlike login(): TestRestTemplate
 * defaults to the JDK's legacy HttpURLConnection, which unconditionally refuses to hand back
 * a 401/407 response body once the request body has been sent in any streaming mode -
 * "cannot retry due to server authentication, in streaming mode" - a JDK limitation with
 * nothing to do with this endpoint's own correctness (confirmed with the real, rebuilt
 * server: curl gets the response body over the wire without trouble). No test anywhere else
 * in this codebase POSTs a body to an endpoint that can answer 401, which is exactly why this
 * had never been hit before. The fix is a local RestTemplate on java.net.http.HttpClient
 * (JdkClientHttpRequestFactory - already part of spring-web, no new dependency) plus a
 * never-throws error handler matching TestRestTemplate's own behaviour, so a 4xx/5xx still
 * comes back as an ordinary ResponseEntity to assert on instead of an exception.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RefreshAndLogoutTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private RestTemplate httpClient;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User u = new User();
        u.setEmail("refresh@test.it");
        u.setUsername("refresh-user");
        u.setPassword(passwordEncoder.encode("password-di-prova"));
        u.setName("Refresh User");
        u.setRole(Role.USER);
        u.setRegisteredAt(LocalDateTime.now());
        userRepository.save(u);

        httpClient = new RestTemplate(new JdkClientHttpRequestFactory());
        httpClient.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                // Every status is "not an error" as far as this client is concerned: the
                // assertions below decide what a given status means, the same contract
                // TestRestTemplate's own default error handler already gives every other
                // test in this file.
                return false;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login() {
        ResponseEntity<String> resp = rest.postForEntity("/api/auth/login",
                Map.of("email", "refresh@test.it", "password", "password-di-prova"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) TestJson.bodyOf(resp).get("data");
    }

    private ResponseEntity<String> refresh(String refreshToken) {
        return post("/api/auth/refresh", refreshToken);
    }

    private ResponseEntity<String> logout(String refreshToken) {
        return post("/api/auth/logout", refreshToken);
    }

    private ResponseEntity<String> post(String path, String refreshToken) {
        // HashMap, not Map.of: refreshToken is null in some of the tests below, and Map.of
        // throws NullPointerException on a null value rather than encoding one.
        Map<String, String> body = new HashMap<>();
        body.put("refreshToken", refreshToken);
        return httpClient.postForEntity(rest.getRootUri() + path, new HttpEntity<>(body), String.class);
    }

    @Test
    void loginIssuesARefreshTokenAlongsideTheAccessToken() {
        Map<String, Object> data = login();

        assertThat(data.get("token")).isNotNull();
        assertThat(data.get("refreshToken")).isNotNull();
        assertThat(data.get("refreshToken")).isNotEqualTo(data.get("token"));
    }

    @Test
    void refreshingWithNoTokenIsRejected() {
        ResponseEntity<String> resp = refresh(null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("MISSING_REFRESH_TOKEN");
    }

    @Test
    void refreshingWithAnUnknownTokenIsRejected() {
        ResponseEntity<String> resp = refresh("questo-token-non-e-mai-stato-emesso");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void aValidRefreshTokenYieldsANewTokenPairAndStopsWorkingItself() throws Exception {
        String original = (String) login().get("refreshToken");

        ResponseEntity<String> resp = refresh(original);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.bodyOf(resp).get("data");
        assertThat(data.get("token")).isNotNull();
        String rotated = (String) data.get("refreshToken");
        assertThat(rotated).isNotNull().isNotEqualTo(original);

        // rotation, not reuse: the token just spent no longer works
        ResponseEntity<String> reuseAttempt = refresh(original);
        assertThat(reuseAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(TestJson.asMap(reuseAttempt.getBody()).get("error")).isEqualTo("INVALID_REFRESH_TOKEN");

        // but the new one from the rotation is live
        ResponseEntity<String> secondRefresh = refresh(rotated);
        assertThat(secondRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theNewAccessTokenFromARefreshActuallyAuthenticates() throws Exception {
        String original = (String) login().get("refreshToken");
        ResponseEntity<String> refreshResp = refresh(original);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.bodyOf(refreshResp).get("data");
        String newAccessToken = (String) data.get("token");

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(newAccessToken);
        ResponseEntity<String> me = rest.exchange("/api/me", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logoutSucceedsForAnUnknownTokenTooWithoutLeakingWhetherItExisted() {
        ResponseEntity<String> resp = logout("mai-esistito");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.asMap(resp.getBody()).get("success")).isEqualTo(true);
    }

    @Test
    void afterLogoutTheRefreshTokenNoLongerWorks() throws Exception {
        String refreshToken = (String) login().get("refreshToken");

        ResponseEntity<String> logoutResp = logout(refreshToken);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> afterLogout = refresh(refreshToken);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(TestJson.asMap(afterLogout.getBody()).get("error")).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshAndLogoutNeedNoAuthorizationHeaderAtAll() {
        // Both are public on purpose - see AuthController's javadoc on why - checked here by
        // simply never sending one across this whole file, and confirming neither answers
        // 401 for that reason (INVALID_REFRESH_TOKEN, not an authentication-entry-point
        // refusal, is what an unrecognised token looks like here).
        ResponseEntity<String> refreshResp = refresh("non-importa-cosa");
        ResponseEntity<String> logoutResp = logout("non-importa-cosa");

        assertThat(TestJson.asMap(refreshResp.getBody()).get("error")).isEqualTo("INVALID_REFRESH_TOKEN");
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
