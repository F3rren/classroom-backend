package com.prenotazioni.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.function.Function;

/**
 * La meta' di sola lettura di JwtService: verifica la firma e legge i claim.
 *
 * E' il pezzo che rende la separazione in servizi economica. Validare un token qui non
 * comporta alcun accesso al database ne' alcuna chiamata verso auth-service: basta
 * conoscere il segreto. Ogni servizio decide da solo chi sta chiamando e con quale ruolo,
 * e di conseguenza i test di ogni servizio possono firmarsi i propri token invece di
 * dover creare un utente vero.
 *
 * L'emissione (generateToken) resta fuori di qui, perche' richiede l'entita' Utente e
 * appartiene al solo servizio che possiede la tabella utenti.
 *
 * Il rovescio della medaglia da tenere presente: il segreto e' ora condiviso fra piu'
 * processi, quindi ruotarlo richiede di riavviarli tutti insieme.
 */
@Component
public class JwtVerifier {

    private static final Logger logger = LoggerFactory.getLogger(JwtVerifier.class);

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // Il segreto e' codificato in BASE64URL, non sono byte grezzi: decodificarlo
        // diversamente produrrebbe una chiave diversa e ogni token verrebbe rifiutato.
        this.key = JwtKey.da(secret);
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            logger.warn("Validazione token fallita: {}", e.getMessage());
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public String getEmailFromToken(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Il nome visualizzato dell'utente.
     *
     * Sta nel token per una ragione precisa: e' l'unico dato dell'utente che serve ai
     * servizi a valle (finisce nel campo "user" dei dettagli aula), e portarlo qui evita
     * a prenotazione-service una chiamata di rete verso auth-service a ogni prenotazione.
     * Un token emesso prima di questa modifica non ha il claim: torna null, e chi lo usa
     * deve gestirlo invece di dare per scontato che ci sia.
     */
    public String getUsernameFromToken(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    public String getNomeFromToken(String token) {
        return extractClaim(token, claims -> claims.get("nome", String.class));
    }

    public String getRoleFromToken(String token) {
        return extractClaim(token, claims -> claims.get("ruolo", String.class));
    }

    public Long getUserIdFromToken(String token) {
        return extractClaim(token, claims -> claims.get("id", Long.class));
    }
}
