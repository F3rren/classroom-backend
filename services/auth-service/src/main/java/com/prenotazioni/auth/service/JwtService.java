package com.prenotazioni.auth.service;

import com.prenotazioni.security.JwtKey;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.prenotazioni.auth.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    // Emette i token e basta: la verifica vive in shared/JwtVerifier, perche' la fanno
    // tutti i servizi mentre firmare tocca solo a chi possiede la tabella utenti.
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key;

    private final long EXPIRATION = 1000 * 60 * 60; // 1 ora

    @PostConstruct
    public void init() {
        this.key = JwtKey.da(secret);
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("id", user.getId())
                // Serve a prenotazione-service per salvare il nome di chi prenota senza
                // interrogare il servizio utenti: vedi JwtVerifier.getNomeFromToken.
                .claim("name", user.getName())
                .claim("username", user.getUsername())
                .claim("role", user.getRole() != null ? user.getRole().getValue() : null)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key)
                .compact();
    }
}
