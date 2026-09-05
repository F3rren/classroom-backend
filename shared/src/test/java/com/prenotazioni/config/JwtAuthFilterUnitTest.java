package com.prenotazioni.config;

import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.security.JwtVerifier;
import com.prenotazioni.testsupport.TestJwt;
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
 * Il filtro che trasforma un token in un'identita' autenticata, per ogni richiesta di
 * ogni servizio.
 *
 * Il caso piu' importante e' quello del ruolo: hasRole('ADMIN') cerca l'authority
 * "ROLE_ADMIN", e il prefisso viene da Ruolo.toAuthority(). Se quel legame si rompesse,
 * ogni endpoint amministrativo diventerebbe inaccessibile agli admin, oppure - molto
 * peggio - un ruolo non riconosciuto potrebbe finire per concedere authority indebite.
 */
class JwtAuthFilterUnitTest {

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        JwtVerifier verifier = new JwtVerifier();
        ReflectionTestUtils.setField(verifier, "secret", TestJwt.SEGRETO_DI_TEST);
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
        Authentication auth = eseguiConHeader("Bearer " + TestJwt.perUtente(7L, "mario@example.it"));

        assertThat(auth).isNotNull();
        AppPrincipal principal = (AppPrincipal) auth.getPrincipal();
        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.getName()).isEqualTo("mario@example.it");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void unTokenDaAdminPortaLAuthorityCheCercaPreAuthorize() throws Exception {
        Authentication auth = eseguiConHeader("Bearer " + TestJwt.perAdmin(1L, "admin@example.it"));

        // hasRole('ADMIN') cerca esattamente questa stringa
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void noHeaderLeavesTheRequestUnauthenticated() throws Exception {
        assertThat(eseguiConHeader(null)).isNull();
    }

    @Test
    void unaIntestazioneSenzaIlPrefissoBearerVieneIgnorata() throws Exception {
        assertThat(eseguiConHeader(TestJwt.perUtente(7L, "mario@example.it"))).isNull();
    }

    @Test
    void anInvalidTokenLeavesTheRequestUnauthenticated() throws Exception {
        // Non solleva e non risponde da solo: lascia proseguire la catena, e sara' la
        // policy di sicurezza a rispondere 401 tramite ApiAuthenticationEntryPoint.
        assertThat(eseguiConHeader("Bearer token-inventato")).isNull();
        assertThat(eseguiConHeader("Bearer " + TestJwt.scaduto(7L, "mario@example.it"))).isNull();
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
