package com.classroom.config;

import com.classroom.security.AppPrincipal;
import com.classroom.security.JwtVerifier;
import com.classroom.testsupport.TestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter that turns a token into an authenticated identity, on every request of every
 * service.
 *
 * The most important case is the role: hasRole('ADMIN') looks for the authority
 * "ROLE_ADMIN", and the prefix comes from Role.toAuthority(). If that link broke, every
 * administrative endpoint would become unreachable to admins, or - far worse - an
 * unrecognised role could end up granting authorities it should not.
 */
class JwtAuthFilterUnitTest {

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        JwtVerifier verifier = new JwtVerifier();
        ReflectionTestUtils.setField(verifier, "secret", TestJwt.TEST_SECRET);
        verifier.init();
        filter = new JwtAuthFilter(verifier);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication eseguiConHeader(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void unTokenValidoDiventaUnUtenteAutenticato() throws Exception {
        Authentication auth = eseguiConHeader("Bearer " + TestJwt.forUser(7L, "mario@example.it"));

        assertThat(auth).isNotNull();
        AppPrincipal principal = (AppPrincipal) auth.getPrincipal();
        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.getName()).isEqualTo("mario@example.it");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void unTokenDaAdminPortaLAuthorityCheCercaPreAuthorize() throws Exception {
        Authentication auth = eseguiConHeader("Bearer " + TestJwt.forAdmin(1L, "admin@example.it"));

        // hasRole('ADMIN') looks for exactly this string
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void noHeaderLeavesTheRequestUnauthenticated() throws Exception {
        assertThat(eseguiConHeader(null)).isNull();
    }

    @Test
    void unaIntestazioneSenzaIlPrefissoBearerVieneIgnorata() throws Exception {
        assertThat(eseguiConHeader(TestJwt.forUser(7L, "mario@example.it"))).isNull();
    }

    @Test
    void anInvalidTokenLeavesTheRequestUnauthenticated() throws Exception {
        // It neither throws nor answers on its own: it lets the chain continue, and it is
        // policy di sicurezza a rispondere 401 tramite ApiAuthenticationEntryPoint.
        assertThat(eseguiConHeader("Bearer token-inventato")).isNull();
        assertThat(eseguiConHeader("Bearer " + TestJwt.expired(7L, "mario@example.it"))).isNull();
    }

    @Test
    void laRottaDiLoginSaltaDelTuttoIlFiltro() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
