package com.prenotazioni.util;

/**
 * Masking of personal data before it reaches the logs.
 *
 * Log files are archived, copied and often shared for debugging: writing
 * dentro l'email in chiaro significa duplicare dati personali fuori dal database, dove
 * no longer subject to the deletion of the user. Masking still keeps the ability to
 * correlate several lines belonging to the same user during an investigation.
 *
 * The logic already existed as a private method in AuthController: it is promoted here to a
 * shared utility because the other controllers and the services logged the email in clear.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /**
     * "mario.rossi@example.it" -> "m***@example.it".
     * The domain stays readable (useful for telling environments or tenants apart), the
     * identifying part does not. Null or malformed input collapses to "***" without
     * throwing:
     * a logging helper must never be able to fail the flow that called it.
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
     * Masks a non-email identifier (a username, say) by showing only its first letter.
     */
    public static String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "***";
        }
        return username.charAt(0) + "***";
    }
}
