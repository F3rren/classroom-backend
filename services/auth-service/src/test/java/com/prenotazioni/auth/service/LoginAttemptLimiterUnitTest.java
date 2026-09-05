package com.prenotazioni.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il limitatore dei tentativi di login.
 *
 * Il test che conta e' quello sulla pulizia: la classe e' nata perche' la mappa non veniva
 * mai svuotata, e senza un test che lo fissi il difetto puo' rientrare senza che nessuno
 * se ne accorga - non produce errori, produce solo memoria che non torna indietro.
 */
class LoginAttemptLimiterUnitTest {

    private LoginAttemptLimiter attemptLimiter(int massimo, long finestraMs, int tetto) {
        return new LoginAttemptLimiter(massimo, finestraMs, tetto);
    }

    @Test
    void lasciaPassareFinoAlLimite() {
        LoginAttemptLimiter l = attemptLimiter(3, 60_000, 1000);

        assertThat(l.troppiTentativi("a")).isFalse();
        assertThat(l.troppiTentativi("a")).isFalse();
        assertThat(l.troppiTentativi("a")).isFalse();
        assertThat(l.troppiTentativi("a")).isTrue();
    }

    @Test
    void ogniChiaveHaIlSuoContatore() {
        // Se i contatori non fossero separati, un solo attaccante basterebbe a bloccare
        // il login di tutti gli altri.
        LoginAttemptLimiter l = attemptLimiter(1, 60_000, 1000);

        l.troppiTentativi("primo");
        assertThat(l.troppiTentativi("primo")).isTrue();
        assertThat(l.troppiTentativi("secondo")).isFalse();
    }

    @Test
    void laFinestraSiRiapre() {
        // Finestra negativa e non zero: con zero due chiamate nello stesso millisecondo
        // danno differenza 0, che non supera la soglia, e il test dipenderebbe
        // dall'orologio. Con -1 la condizione e' vera per costruzione.
        LoginAttemptLimiter l = attemptLimiter(1, -1, 1000);

        l.troppiTentativi("a");
        assertThat(l.troppiTentativi("a")).isFalse();
    }

    @Test
    void laPuliziaTogliLeChiaviScadute() {
        // LA regressione da tenere chiusa. Prima nessuna chiave usciva mai, e la parte
        // email della chiave la sceglie chi chiama: la memoria cresceva su richiesta.
        LoginAttemptLimiter l = attemptLimiter(5, 1000, 1000);

        for (int i = 0; i < 200; i++) {
            l.troppiTentativi("indirizzo-inventato-" + i + "@esempio.it");
        }
        assertThat(l.chiaviInMemoria()).isEqualTo(200);

        l.ripulisci(System.currentTimeMillis() + 5000);

        assertThat(l.chiaviInMemoria()).isZero();
    }

    @Test
    void laPuliziaRisparmiaLeChiaviAncoraDentroLaFinestra() {
        // Ripulire troppo sarebbe l'errore opposto: azzererebbe i contatori di chi sta
        // attaccando adesso, cioe' proprio quelli che servono.
        LoginAttemptLimiter l = attemptLimiter(5, 600_000, 1000);

        l.troppiTentativi("ancora-viva@esempio.it");
        l.ripulisci(System.currentTimeMillis());

        assertThat(l.chiaviInMemoria()).isEqualTo(1);
    }

    @Test
    void alTettoSiRipuliscePrimaDiRinunciare() {
        // Il tetto non deve scattare finche' c'e' roba scaduta da buttare: prima si libera,
        // e solo se dopo la pulizia si e' ancora al limite si smette di registrare.
        // Stessa ragione: una finestra di 1 ms sarebbe scaduta o no a seconda di quanto
        // in fretta gira il ciclo. Negativa, sono scadute per costruzione.
        LoginAttemptLimiter l = attemptLimiter(5, -1, 50);

        for (int i = 0; i < 50; i++) {
            l.troppiTentativi("vecchia-" + i);
        }
        assertThat(l.chiaviInMemoria()).isEqualTo(50);

        l.troppiTentativi("nuova");

        assertThat(l.chiaviInMemoria()).isLessThan(50);
    }

    @Test
    void alTettoLeChiaviGiaNoteRestanoLimitate() {
        // Il fallimento e' aperto solo per le chiavi NUOVE: chi sta gia' attaccando
        // continua a essere contato, altrimenti riempire la mappa sarebbe il modo per
        // disattivare il limitatore.
        LoginAttemptLimiter l = attemptLimiter(1, 600_000, 2);

        l.troppiTentativi("nota");
        assertThat(l.troppiTentativi("nota")).isTrue();

        l.troppiTentativi("seconda");
        // il tetto e' pieno e niente e' scaduto: la terza chiave non viene registrata
        assertThat(l.troppiTentativi("terza")).isFalse();
        assertThat(l.chiaviInMemoria()).isEqualTo(2);

        // ma quella nota continua a essere limitata
        assertThat(l.troppiTentativi("nota")).isTrue();
    }
}
