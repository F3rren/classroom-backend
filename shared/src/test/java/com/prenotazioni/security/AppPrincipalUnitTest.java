package com.prenotazioni.security;

import com.prenotazioni.model.Ruolo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AppPrincipal e' il punto in cui i claim del token diventano decisioni di autorizzazione,
 * quindi ogni servizio della futura architettura a microservizi ci passa attraverso: e' lui
 * a rendere superflua una chiamata di rete verso auth-service per sapere chi sta chiamando.
 */
class AppPrincipalUnitTest {

    @Test
    void getNameReturnsTheEmailBecauseCallersExpectIt() {
        // il codice esistente chiama Authentication.getName() aspettandosi l'email
        AppPrincipal principal = new AppPrincipal(7L, "mario.rossi@example.it", "Mario Rossi", "user");

        assertThat(principal.getName()).isEqualTo("mario.rossi@example.it");
        assertThat(principal.id()).isEqualTo(7L);
    }

    @Test
    void isAdminRecognisesTheRoleWhateverTheCasing() {
        assertThat(new AppPrincipal(1L, "a@b.it", "Mario Rossi", Ruolo.ADMIN.getValore()).isAdmin()).isTrue();
        assertThat(new AppPrincipal(1L, "a@b.it", "Mario Rossi", "ADMIN").isAdmin()).isTrue();
        assertThat(new AppPrincipal(1L, "a@b.it", "Mario Rossi", "Admin").isAdmin()).isTrue();
    }

    @Test
    void isAdminIsFalseForEveryoneElse() {
        assertThat(new AppPrincipal(1L, "a@b.it", "Mario Rossi", Ruolo.USER.getValore()).isAdmin()).isFalse();
        assertThat(new AppPrincipal(1L, "a@b.it", "Mario Rossi", "superuser").isAdmin()).isFalse();
        // un token senza claim di ruolo non deve promuovere nessuno ad admin
        assertThat(new AppPrincipal(1L, "a@b.it", "Mario Rossi", null).isAdmin()).isFalse();
    }
}
