package com.classroom.auth.service;

import com.classroom.security.JwtKey;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.classroom.auth.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    // It issues tokens and nothing else: verification lives in shared/JwtVerifier, because
    // every service verifies while only the owner of the users table signs.
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key;

    private final long EXPIRATION = 1000 * 60 * 60; // 1 hour

    @PostConstruct
    public void init() {
        this.key = JwtKey.from(secret);
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("id", user.getId())
                // booking-service needs this to store the name of whoever booked without
                // querying the user service: see JwtVerifier.getNameFromToken.
                .claim("name", user.getName())
                .claim("username", user.getUsername())
                .claim("role", user.getRole() != null ? user.getRole().getValue() : null)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key)
                .compact();
    }
}
