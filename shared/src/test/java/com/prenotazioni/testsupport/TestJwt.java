package com.prenotazioni.testsupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Firma token di prova, senza che esista un utente ne' un servizio di autenticazione.
 *
 * E' cio' che rende sostenibile la separazione in servizi sul fronte dei test. Prima ogni
 * classe di integrazione creava un utente vero e chiamava /api/auth/login solo per
 * ottenere un token: una dipendenza dal dominio utenti che non c'entrava nulla con cio'
 * che il test voleva verificare. Otto classi su dieci attraversavano tre o quattro domini
 * per questo motivo.
 *
 * Funziona perche' la validazione e' offline: JwtVerifier controlla la firma e legge i
 * claim, senza mai consultare un database. Un token firmato qui con lo stesso segreto e'
 * indistinguibile da uno emesso da auth-service.
 *
 * Vive nei sorgenti di test di shared e viene pubblicato come test-jar, cosi' gli altri
 * moduli lo usano senza che finisca nell'artefatto di produzione.
 */
public final class TestJwt {

    /** Deve coincidere con jwt.secret dei profili di test. */
    public static final String SEGRETO_DI_TEST = "dGVzdC1zZWNyZXQtcGVyLWktdGVzdC1kaS1pbnRlZ3JhemlvbmUtMDAxMg";

    private static final long DURATA_MS = 1000L * 60 * 60;

    private TestJwt() {
    }

    /** Token per un utente qualunque. Il nome e' derivato dall'email. */
    public static String forUser(Long id, String email) {
        return firma(id, email, nomeDa(email), "user");
    }

    /** Token per un utente di cui conta il nome: finisce nei dettagli aula. */
    public static String forUser(Long id, String email, String name) {
        return firma(id, email, name, "user");
    }

    /** Token per un amministratore. */
    public static String forAdmin(Long id, String email) {
        return firma(id, email, nomeDa(email), "admin");
    }

    /** Token gia' scaduto, per verificare che venga rifiutato. */
    public static String scaduto(Long id, String email) {
        return costruisci(id, email, nomeDa(email), "user", new Date(System.currentTimeMillis() - DURATA_MS));
    }

    /** Token senza il claim "name": simula un token emesso prima che venisse introdotto. */
    public static String senzaNome(Long id, String email) {
        return costruisci(id, email, null, "user", new Date(System.currentTimeMillis() + DURATA_MS));
    }

    private static String nomeDa(String email) {
        return email == null ? null : email.split("@")[0];
    }

    private static String firma(Long id, String email, String name, String role) {
        return costruisci(id, email, name, role, new Date(System.currentTimeMillis() + DURATA_MS));
    }

    private static String costruisci(Long id, String email, String name, String role, Date scadenza) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SEGRETO_DI_TEST));
        return Jwts.builder()
                .subject(email)
                .claim("id", id)
                .claim("name", name)
                .claim("username", name == null ? null : name.toLowerCase().replace(" ", "."))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(scadenza)
                .signWith(key)
                .compact();
    }
}
