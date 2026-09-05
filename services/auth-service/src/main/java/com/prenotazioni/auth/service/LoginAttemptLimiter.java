package com.prenotazioni.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Contatore dei tentativi di login falliti, a finestra fissa e in memoria.
 *
 * Prima viveva dentro AuthController come campo static, e aveva due difetti che si
 * tenevano per mano.
 *
 * IL PRIMO, ed e' il motivo per cui questa classe esiste: la mappa non veniva MAI
 * svuotata. Una sola scrittura, computeIfAbsent, e nessuna rimozione: ogni coppia mai
 * vista restava dentro per sempre, e la parte email della chiave la sceglie chi chiama.
 * La crescita quindi non dipendeva dal numero di utenti veri ma da quante stringhe
 * diverse qualcuno decideva di inviare a un endpoint pubblico.
 *
 * IL SECONDO era lo static in se': i test dovevano azzerare la mappa a mano fra un caso
 * e l'altro perche' surefire riusa la JVM, e il profilo di test alzava max-attempts a
 * 1000 per non far scattare il limite nelle altre classi. Un componente normale, uno per
 * contesto, toglie il problema invece di aggirarlo.
 *
 * SUL FALLIRE APERTO. Se la pulizia non basta - cioe' se ci sono davvero decine di
 * migliaia di chiavi ancora dentro la finestra - questa classe smette di registrare
 * chiavi nuove invece di continuare a crescere. E' una scelta deliberata: un limitatore
 * e' una difesa migliore, non l'unica, e restare in piedi senza limitare vale piu' che
 * cadere per esaurimento di memoria portandosi dietro anche i login legittimi. Le chiavi
 * gia' note continuano a essere limitate.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private final int massimoTentativi;
    private final long windowMs;
    private final int tettoChiavi;

    /** Per non ripetere lo stesso avviso a ogni richiesta quando la mappa e' piena. */
    private volatile long ultimoAvviso;

    public LoginAttemptLimiter(
            @Value("${auth.rate-limit.max-attempts:5}") int massimoTentativi,
            @Value("${auth.rate-limit.window-ms:60000}") long windowMs,
            @Value("${auth.rate-limit.max-entries:50000}") int tettoChiavi) {
        this.massimoTentativi = massimoTentativi;
        this.windowMs = windowMs;
        this.tettoChiavi = tettoChiavi;
    }

    /** Registra un tentativo e dice se la chiave ha superato il limite. */
    public boolean tooManyAttempts(String key) {
        long adesso = System.currentTimeMillis();

        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= tettoChiavi) {
                purgeExpired(adesso);
            }
            if (windows.size() >= tettoChiavi) {
                avvisaSaltuariamente(adesso);
                return false;
            }
            window = windows.computeIfAbsent(key, k -> new Window(adesso));
        }

        synchronized (window) {
            if (adesso - window.startTime > windowMs) {
                window.startTime = adesso;
                window.tentativi = 0;
            }
            window.tentativi++;
            return window.tentativi > massimoTentativi;
        }
    }

    /**
     * Toglie le chiavi la cui finestra e' finita: da quel momento non contano piu' nulla,
     * e tenerle in giro sarebbe solo memoria occupata.
     *
     * Visibile ai test di proposito: la pulizia e' la ragione d'essere della classe, e va
     * potuta verificare senza aspettare che accada da sola.
     */
    void purgeExpired(long adesso) {
        int prima = windows.size();
        windows.entrySet().removeIf(voce -> {
            Window f = voce.getValue();
            synchronized (f) {
                return adesso - f.startTime > windowMs;
            }
        });
        int rimosse = prima - windows.size();
        if (rimosse > 0) {
            logger.debug("Limitatore login: rimosse {} chiavi scadute, ne restano {}", rimosse, windows.size());
        }
    }

    /** Quante chiavi sono in memoria adesso. Serve ai test e a un'eventuale metrica. */
    int trackedKeys() {
        return windows.size();
    }

    private void avvisaSaltuariamente(long adesso) {
        if (adesso - ultimoAvviso > 60_000) {
            ultimoAvviso = adesso;
            logger.warn("Limitatore login al tetto di {} chiavi: i tentativi da chiavi nuove non "
                    + "vengono piu' contati finche' la finestra non si libera. Se non rientra, "
                    + "e' un attacco distribuito e serve una difesa a monte del servizio.", tettoChiavi);
        }
    }

    private static final class Window {
        long startTime;
        int tentativi;

        Window(long startTime) {
            this.startTime = startTime;
        }
    }
}
