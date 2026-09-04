package com.prenotazioni.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Lettura del JSON nelle risposte HTTP dei test.
 *
 * Esisteva gia', undici volte: ogni classe di test d'integrazione si costruiva il proprio
 * ObjectMapper e ci avvolgeva intorno un `private Map&lt;String, Object&gt; asMap(String)`
 * identico agli altri dieci. Undici copie della stessa riga sono undici posti dove
 * correggere il giorno in cui serve gestire un caso in piu'.
 *
 * Un ObjectMapper solo, statico, va bene: e' progettato per essere condiviso ed e' sicuro
 * fra thread una volta configurato.
 *
 * Sta qui e non in ciascun modulo perche' i tre servizi dipendono gia' dal test-jar di
 * shared - e' cosi' che usano {@link TestJwt}.
 */
public final class TestJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestJson() {
    }

    /**
     * Il JSON come mappa.
     *
     * Rilancia come RuntimeException di proposito: in un test un JSON illeggibile non e' un
     * caso da gestire, e' il test che deve fallire subito indicando cosa e' arrivato.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> comeMappa(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("JSON non leggibile: " + json, e);
        }
    }

    /** Il corpo di una risposta, gia' come mappa. */
    public static Map<String, Object> corpoDi(ResponseEntity<String> risposta) {
        return comeMappa(risposta.getBody());
    }
}
