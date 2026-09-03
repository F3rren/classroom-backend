package com.prenotazioni.security;

import com.prenotazioni.testsupport.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La verifica dei token e' il perno dell'architettura a servizi: e' cio' che permette a
 * ogni servizio di sapere chi sta chiamando senza interrogare nessuno. Finche' viveva in
 * app era esercitata di riflesso dai test di integrazione; spostata in shared restava
 * senza un solo test, ed e' codice di sicurezza.
 *
 * Il caso che conta di piu' e' quello del token firmato con un altro segreto: se passasse,
 * chiunque potrebbe fabbricarsi un'identita' con il ruolo che preferisce.
 */
class JwtVerifierUnitTest {

    private JwtVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new JwtVerifier();
        ReflectionTestUtils.setField(verifier, "secret", TestJwt.SEGRETO_DI_TEST);
        verifier.init();
    }

    @Test
    void acceptsATokenSignedWithTheSharedSecret() {
        String token = TestJwt.perUtente(7L, "mario.rossi@example.it");

        assertThat(verifier.validateToken(token)).isTrue();
        assertThat(verifier.getUserIdFromToken(token)).isEqualTo(7L);
        assertThat(verifier.getEmailFromToken(token)).isEqualTo("mario.rossi@example.it");
        assertThat(verifier.getRuoloFromToken(token)).isEqualTo("user");
    }

    @Test
    void readsTheAdminRoleFromTheClaim() {
        String token = TestJwt.perAdmin(1L, "admin@example.it");

        assertThat(verifier.getRuoloFromToken(token)).isEqualTo("admin");
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        // Un altro segreto, stesso formato: e' il tentativo di forgiare un'identita'
        JwtVerifier altroServizio = new JwtVerifier();
        ReflectionTestUtils.setField(altroServizio, "secret",
                "dW4tc2VncmV0by1jb21wbGV0YW1lbnRlLWRpdmVyc28tZGEtcXVlbGxvLXZlcm8");
        altroServizio.init();

        assertThat(verifier.validateToken(TestJwt.perAdmin(1L, "intruso@example.it"))).isTrue();
        assertThat(altroServizio.validateToken(TestJwt.perAdmin(1L, "intruso@example.it"))).isFalse();
    }

    @Test
    void rejectsATamperedSignature() {
        String token = TestJwt.perUtente(7L, "mario@example.it");
        String manomesso = token.substring(0, token.lastIndexOf('.')) + ".firmaAlterata";

        assertThat(verifier.validateToken(manomesso)).isFalse();
    }

    @Test
    void rejectsAnExpiredToken() {
        assertThat(verifier.validateToken(TestJwt.scaduto(7L, "mario@example.it"))).isFalse();
    }

    @Test
    void rejectsGarbageWithoutThrowing() {
        // Il filtro chiama validateToken su qualunque cosa arrivi nell'header:
        // deve rispondere false, non propagare un'eccezione.
        assertThat(verifier.validateToken("non-e-un-token")).isFalse();
        assertThat(verifier.validateToken("")).isFalse();
    }

    @Test
    void leggeIlNomeDalClaimDedicato() {
        // Il nome viaggia nel token proprio per evitare che prenotazione-service debba
        // chiedere ad auth-service chi sta prenotando.
        String token = TestJwt.perUtente(7L, "mario.rossi@example.it", "Mario Rossi");

        assertThat(verifier.getNomeFromToken(token)).isEqualTo("Mario Rossi");
    }

    @Test
    void unTokenEmessoPrimaDelClaimNomeNonFaFallireNulla() {
        // I token gia' in circolazione non hanno il claim: devono restare validi e
        // il nome deve semplicemente mancare, non far esplodere il filtro.
        String tokenVecchio = TestJwt.senzaNome(7L, "mario.rossi@example.it");

        assertThat(verifier.validateToken(tokenVecchio)).isTrue();
        assertThat(verifier.getNomeFromToken(tokenVecchio)).isNull();
        assertThat(verifier.getUserIdFromToken(tokenVecchio)).isEqualTo(7L);
    }
}
