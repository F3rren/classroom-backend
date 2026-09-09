package com.classroom.auth;

import com.classroom.auth.model.User;
import com.classroom.auth.repository.UserRepository;
import com.classroom.model.Role;
import com.classroom.security.SessionCookies;
import com.classroom.testsupport.TestJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The session carried by HttpOnly cookies, over real HTTP.
 *
 * A browser cannot hold these tokens safely - anything the page can read, an injected script
 * can read too - so login also hands them over as cookies the page has no access to. What
 * matters here is that the whole cycle works with the cookies ALONE: the browser has no way
 * to put a refresh token in a request body, so if refresh or logout still demanded one, the
 * cookie flow would be unusable for exactly the clients it exists to protect.
 *
 * The header flow is checked too, and on purpose: these cookies are an addition, and a
 * client that keeps sending Authorization must keep working exactly as before.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionCookieTest {

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
        u.setEmail("cookie@test.it");
        u.setUsername("cookie-user");
        u.setPassword(passwordEncoder.encode("password-di-prova"));
        u.setName("Cookie User");
        u.setRole(Role.USER);
        u.setRegisteredAt(LocalDateTime.now());
        userRepository.save(u);

        // Same reason as RefreshAndLogoutTest: the JDK's legacy HttpURLConnection refuses to
        // return a 401 body once a request body has been streamed.
        httpClient = new RestTemplate(new JdkClientHttpRequestFactory());
        httpClient.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
    }

    // ==================== helpers ====================

    private ResponseEntity<String> login() {
        ResponseEntity<String> resp = rest.postForEntity("/api/auth/login",
                Map.of("email", "cookie@test.it", "password", "password-di-prova"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp;
    }

    /** The Set-Cookie line for a given cookie, or null when the response does not set it. */
    private String setCookie(ResponseEntity<String> response, String name) {
        List<String> headers = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (headers == null) {
            return null;
        }
        return headers.stream().filter(h -> h.startsWith(name + "=")).findFirst().orElse(null);
    }

    /** The value of a cookie as the browser would store it. */
    private String cookieValue(ResponseEntity<String> response, String name) {
        String header = setCookie(response, name);
        if (header == null) {
            return null;
        }
        String value = header.substring(name.length() + 1);
        int end = value.indexOf(';');
        return end >= 0 ? value.substring(0, end) : value;
    }

    /** Sends a request carrying cookies and nothing else - no Authorization, no body. */
    private ResponseEntity<String> withCookies(String path, HttpMethod method, String cookieHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        return httpClient.exchange(rest.getRootUri() + path, method, new HttpEntity<>(headers), String.class);
    }

    private String cookiePair(String name, String value) {
        return name + "=" + value;
    }

    // ==================== login ====================

    @Test
    void loginSetsBothTokensAsHttpOnlyCookies() {
        ResponseEntity<String> resp = login();

        String access = setCookie(resp, SessionCookies.ACCESS_TOKEN);
        String refresh = setCookie(resp, SessionCookies.REFRESH_TOKEN);

        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        // HttpOnly is the whole point: without it the page could read the token back.
        assertThat(access).contains("HttpOnly").contains("Path=/").contains("SameSite=Lax");
        assertThat(refresh).contains("HttpOnly").contains("Path=/").contains("SameSite=Lax");
    }

    @Test
    void theCookiesCarryTheSameTokensAsTheBody() {
        ResponseEntity<String> resp = login();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.bodyOf(resp).get("data");

        // The body keeps carrying them, so a non-browser client is unaffected.
        assertThat(cookieValue(resp, SessionCookies.ACCESS_TOKEN)).isEqualTo(data.get("token"));
        assertThat(cookieValue(resp, SessionCookies.REFRESH_TOKEN)).isEqualTo(data.get("refreshToken"));
    }

    @Test
    void overPlainHttpTheCookiesAreNotMarkedSecure() {
        // A Secure cookie sent over plain HTTP is discarded by the browser without a word,
        // and the session would silently never start in local development.
        ResponseEntity<String> resp = login();

        assertThat(setCookie(resp, SessionCookies.ACCESS_TOKEN)).doesNotContain("Secure");
    }

    // ==================== protected endpoints ====================

    @Test
    void theAccessCookieAloneAuthenticatesAProtectedCall() {
        String access = cookieValue(login(), SessionCookies.ACCESS_TOKEN);

        ResponseEntity<String> me = withCookies("/api/me", HttpMethod.GET,
                cookiePair(SessionCookies.ACCESS_TOKEN, access));

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("cookie@test.it");
    }

    @Test
    void aRequestWithNeitherHeaderNorCookieIsStillRefused() {
        ResponseEntity<String> me = httpClient.getForEntity(rest.getRootUri() + "/api/me", String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anInvalidAccessCookieIsRefused() {
        ResponseEntity<String> me = withCookies("/api/me", HttpMethod.GET,
                cookiePair(SessionCookies.ACCESS_TOKEN, "non-e-un-token"));

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theAuthorizationHeaderStillWins() {
        String access = cookieValue(login(), SessionCookies.ACCESS_TOKEN);

        // Header valid, cookie rubbish: the header is read first, so this must succeed.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(access);
        headers.add(HttpHeaders.COOKIE, cookiePair(SessionCookies.ACCESS_TOKEN, "spazzatura"));

        ResponseEntity<String> me = httpClient.exchange(rest.getRootUri() + "/api/me",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ==================== refresh ====================

    @Test
    void refreshWorksFromTheCookieWithNoBody() {
        String refresh = cookieValue(login(), SessionCookies.REFRESH_TOKEN);

        ResponseEntity<String> resp = withCookies("/api/auth/refresh", HttpMethod.POST,
                cookiePair(SessionCookies.REFRESH_TOKEN, refresh));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refreshReplacesBothCookies() {
        ResponseEntity<String> first = login();
        String oldRefresh = cookieValue(first, SessionCookies.REFRESH_TOKEN);

        ResponseEntity<String> resp = withCookies("/api/auth/refresh", HttpMethod.POST,
                cookiePair(SessionCookies.REFRESH_TOKEN, oldRefresh));

        // The rotation retires the presented refresh token: leaving the browser holding the
        // consumed one would end the session at the following refresh.
        assertThat(cookieValue(resp, SessionCookies.REFRESH_TOKEN))
                .isNotNull()
                .isNotEqualTo(oldRefresh);

        // The access cookie is reissued too, but its value is deliberately NOT compared with
        // the previous one: a JWT's iat and exp claims have one-second resolution, so two
        // tokens minted for the same user inside the same second are byte-identical. That is
        // a property of the token format, not a failure to replace the cookie.
        assertThat(cookieValue(resp, SessionCookies.ACCESS_TOKEN)).isNotNull().isNotBlank();
    }

    @Test
    void refreshAcceptsAnEmptyBodyAlongsideTheCookie() {
        // This is exactly what the browser client sends: it has no token to put in the body,
        // so it posts {} and relies on the cookie. Deserialising that yields a request object
        // with a null token, a different path from sending no body at all.
        String refresh = cookieValue(login(), SessionCookies.REFRESH_TOKEN);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, cookiePair(SessionCookies.REFRESH_TOKEN, refresh));

        ResponseEntity<String> resp = httpClient.exchange(rest.getRootUri() + "/api/auth/refresh",
                HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logoutAcceptsAnEmptyBodyAlongsideTheCookie() {
        String refresh = cookieValue(login(), SessionCookies.REFRESH_TOKEN);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, cookiePair(SessionCookies.REFRESH_TOKEN, refresh));

        ResponseEntity<String> resp = httpClient.exchange(rest.getRootUri() + "/api/auth/logout",
                HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setCookie(resp, SessionCookies.REFRESH_TOKEN)).contains("Max-Age=0");
    }

    @Test
    void aRefreshWithNeitherBodyNorCookieIsStillRejected() {
        ResponseEntity<String> resp = httpClient.postForEntity(
                rest.getRootUri() + "/api/auth/refresh", new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("MISSING_REFRESH_TOKEN");
    }

    // ==================== logout ====================

    @Test
    void logoutWorksFromTheCookieAndClearsBoth() {
        String refresh = cookieValue(login(), SessionCookies.REFRESH_TOKEN);

        ResponseEntity<String> resp = withCookies("/api/auth/logout", HttpMethod.POST,
                cookiePair(SessionCookies.REFRESH_TOKEN, refresh));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Max-Age=0 is what actually removes them: the page cannot delete an HttpOnly cookie.
        assertThat(setCookie(resp, SessionCookies.ACCESS_TOKEN)).contains("Max-Age=0");
        assertThat(setCookie(resp, SessionCookies.REFRESH_TOKEN)).contains("Max-Age=0");
    }

    @Test
    void afterLogoutTheRefreshCookieNoLongerWorks() {
        String refresh = cookieValue(login(), SessionCookies.REFRESH_TOKEN);

        withCookies("/api/auth/logout", HttpMethod.POST, cookiePair(SessionCookies.REFRESH_TOKEN, refresh));

        ResponseEntity<String> resp = withCookies("/api/auth/refresh", HttpMethod.POST,
                cookiePair(SessionCookies.REFRESH_TOKEN, refresh));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
