package com.prenotazioni.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Il formato dei timestamp esposti dalle API.
 *
 * Lo stesso DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") era dichiarato, identico,
 * in cinque classi diverse: ApiEnvelope, LoginResponse, UserSummaryDto, AuthController e
 * PrenotazioneController. Il pattern fa parte del contratto verso il frontend, quindi
 * cinque copie sono cinque punti da cui puo' divergere senza che nulla lo segnali.
 *
 * Sta in util e non dentro ApiEnvelope perche' lo usano sia DTO sia controller: farlo
 * dipendere da un DTO specifico creerebbe un accoppiamento senza motivo.
 */
public final class Timestamps {

    private static final DateTimeFormatter FORMATO_API = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Timestamps() {
    }

    /** Formatta un istante nel formato usato dalle risposte. Null in ingresso, null in uscita. */
    public static String format(LocalDateTime istante) {
        return istante == null ? null : istante.format(FORMATO_API);
    }

    /** Il momento presente, gia' formattato. */
    public static String now() {
        return format(LocalDateTime.now());
    }
}
