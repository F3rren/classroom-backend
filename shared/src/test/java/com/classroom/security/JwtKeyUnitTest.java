package com.classroom.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The secret has to be accepted in either base64 alphabet.
 *
 * The case that led to this class: a secret generated with the very command the
 * documentation suggested, "openssl rand -base64 48", contained a '/' and killed three
 * services at startup with "Illegal base64url character: '/'". Over 64 random characters that
 * happens roughly 87% of the time - it only worked because the secret in use had landed in
 * the lucky 13%.
 */
class JwtKeyUnitTest {

    /** The same 48 bytes in both encodings: they differ only in '+/' versus '-_'. */
    private static final String STANDARD =
            "T3VqK2Zy/2Jhc2U2NCtzdGFuZGFyZC93aXRoK3BsdXMvYW5kL3NsYXNoISE=";
    private static final String URL_SAFE =
            "T3VqK2Zy_2Jhc2U2NCtzdGFuZGFyZC93aXRoK3BsdXMvYW5kL3NsYXNoISE=".replace('+', '-');

    @Test
    void accettaUnSegretoInBase64Standard() {
        // This is the form "openssl rand -base64" produces, which is what anybody following
        // the documentation ends up pasting into the file.
        assertThat(JwtKey.from(STANDARD)).isNotNull();
    }

    @Test
    void accettaUnSegretoInBase64Url() {
        assertThat(JwtKey.from(URL_SAFE)).isNotNull();
    }

    @Test
    void iDueAlfabetiProduconoLaStessaChiave() {
        // This is the point that really matters: the signer and the verifier may hold the
        // secret written in either form, and still have to derive the same key. If this
        // assertion fell, tokens would come out invalid with no clear error.
        SecretKey fromStandard = JwtKey.from(STANDARD);
        SecretKey fromUrlSafe = JwtKey.from(URL_SAFE);

        assertThat(fromStandard.getEncoded()).isEqualTo(fromUrlSafe.getEncoded());
    }

    @Test
    void ignoraGliSpaziAiBordi() {
        // A secret pasted by hand carries stray spaces or a newline more often than you
        // would think, and the error message would not help anybody work that out.
        assertThat(JwtKey.from("  " + STANDARD + "  ").getEncoded())
                .isEqualTo(JwtKey.from(STANDARD).getEncoded());
    }

    @Test
    void unSegretoMancanteDiceCosaImpostare() {
        // Without this, a missing secret arrives as a NullPointerException inside jjwt,
        // which tells nobody which variable is missing.
        assertThatThrownBy(() -> JwtKey.from(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        assertThatThrownBy(() -> JwtKey.from("   "))
                .isInstanceOf(IllegalStateException.class);
    }
}
