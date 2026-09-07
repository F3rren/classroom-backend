package com.prenotazioni.security;

import com.prenotazioni.model.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AppPrincipal is the point where the token's claims become authorisation decisions, so
 * every service passes through it: it is what makes a network call to auth-service
 * unnecessary just to know who is calling.
 */
class AppPrincipalUnitTest {

    @Test
    void getNameReturnsTheEmailBecauseCallersExpectIt() {
        // existing code calls Authentication.getName() expecting the email
        AppPrincipal principal = new AppPrincipal(7L, "mario.rossi@example.it", "m.rossi", "Mario Rossi", "user");

        assertThat(principal.getName()).isEqualTo("mario.rossi@example.it");
        assertThat(principal.id()).isEqualTo(7L);
    }

    @Test
    void isAdminRecognisesTheRoleWhateverTheCasing() {
        assertThat(new AppPrincipal(1L, "a@b.it", "m.rossi", "Mario Rossi", Role.ADMIN.getValue()).isAdmin()).isTrue();
        assertThat(new AppPrincipal(1L, "a@b.it", "m.rossi", "Mario Rossi", "ADMIN").isAdmin()).isTrue();
        assertThat(new AppPrincipal(1L, "a@b.it", "m.rossi", "Mario Rossi", "Admin").isAdmin()).isTrue();
    }

    @Test
    void isAdminIsFalseForEveryoneElse() {
        assertThat(new AppPrincipal(1L, "a@b.it", "m.rossi", "Mario Rossi", Role.USER.getValue()).isAdmin()).isFalse();
        assertThat(new AppPrincipal(1L, "a@b.it", "m.rossi", "Mario Rossi", "superuser").isAdmin()).isFalse();
        // a token with no role claim must not promote anybody to admin
        assertThat(new AppPrincipal(1L, "a@b.it", "m.rossi", "Mario Rossi", null).isAdmin()).isFalse();
    }
}
