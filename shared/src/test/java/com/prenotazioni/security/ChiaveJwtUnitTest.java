package com.prenotazioni.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Il segreto va accettato in entrambi gli alfabeti base64.
 *
 * Il caso che ha portato a questa classe: un segreto generato con il comando che la
 * documentazione stessa suggeriva, "openssl rand -base64 48", conteneva una '/' e faceva
 * morire tre servizi all'avvio con "Illegal base64url character: '/'". Su 64 caratteri
 * casuali succede circa nell'87% dei casi - funzionava solo perche' il segreto in uso era
 * capitato nel 13% fortunato.
 */
class ChiaveJwtUnitTest {

    /** Stessi 48 byte nelle due codifiche: differiscono solo per '+/' contro '-_'. */
    private static final String STANDARD =
            "T3VqK2Zy/2Jhc2U2NCtzdGFuZGFyZC93aXRoK3BsdXMvYW5kL3NsYXNoISE=";
    private static final String URL_SAFE =
            "T3VqK2Zy_2Jhc2U2NCtzdGFuZGFyZC93aXRoK3BsdXMvYW5kL3NsYXNoISE=".replace('+', '-');

    @Test
    void accettaUnSegretoInBase64Standard() {
        // E' la forma che produce "openssl rand -base64", cioe' quella che chiunque
        // segua la documentazione si ritrova incollata nel file.
        assertThat(ChiaveJwt.da(STANDARD)).isNotNull();
    }

    @Test
    void accettaUnSegretoInBase64Url() {
        assertThat(ChiaveJwt.da(URL_SAFE)).isNotNull();
    }

    @Test
    void iDueAlfabetiProduconoLaStessaChiave() {
        // E' il punto che conta davvero: chi firma e chi verifica possono avere il segreto
        // scritto nelle due forme, e devono comunque ottenere la stessa chiave. Se questa
        // asserzione cadesse, i token risulterebbero non validi senza alcun errore chiaro.
        SecretKey daStandard = ChiaveJwt.da(STANDARD);
        SecretKey daUrl = ChiaveJwt.da(URL_SAFE);

        assertThat(daStandard.getEncoded()).isEqualTo(daUrl.getEncoded());
    }

    @Test
    void ignoraGliSpaziAiBordi() {
        // Un segreto incollato a mano si porta dietro spazi o un a capo piu' spesso di
        // quanto si creda, e il messaggio d'errore non aiuterebbe a capirlo.
        assertThat(ChiaveJwt.da("  " + STANDARD + "  ").getEncoded())
                .isEqualTo(ChiaveJwt.da(STANDARD).getEncoded());
    }

    @Test
    void unSegretoMancanteDiceCosaImpostare() {
        // Senza questo, un segreto assente arriva come NullPointerException dentro jjwt,
        // che non dice a nessuno quale variabile manchi.
        assertThatThrownBy(() -> ChiaveJwt.da(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        assertThatThrownBy(() -> ChiaveJwt.da("   "))
                .isInstanceOf(IllegalStateException.class);
    }
}
