package com.prenotazioni.util;

/**
 * Oscuramento dei dati personali prima che finiscano nei log.
 *
 * I file di log vengono archiviati, copiati e spesso condivisi per il debug: scriverci
 * dentro l'email in chiaro significa duplicare dati personali fuori dal database, dove
 * non sono piu' soggetti alla cancellazione dell'utente. Mascherare mantiene comunque
 * la possibilita' di correlare piu' righe dello stesso utente durante un'indagine.
 *
 * La logica era gia' presente come metodo privato in AuthController: qui viene promossa
 * a utility condivisa perche' gli altri controller e i service loggavano l'email in chiaro.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /**
     * "mario.rossi@example.it" -> "m***@example.it".
     * Il dominio resta leggibile (utile per distinguere ambienti/tenant), la parte
     * identificativa no. Input nullo o malformato collassa su "***" senza eccezioni:
     * un helper di logging non deve mai poter far fallire il flusso chiamante.
     */
    public static String maskEmail(String email) {
        if (email == null || email.length() < 3) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * Maschera un identificativo non-email (es. username) mostrando solo la prima lettera.
     */
    public static String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "***";
        }
        return username.charAt(0) + "***";
    }
}
