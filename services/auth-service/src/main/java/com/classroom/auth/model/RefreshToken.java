package com.classroom.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One issued refresh token, on record only as its hash - see V4__refresh_tokens.sql for why.
 *
 * A plain {@code userId} field rather than a {@code @ManyToOne User}, unlike what a "real"
 * JPA relation would look like: this entity is internal plumbing for RefreshTokenService
 * alone, never serialized to a client, so there is nothing to gain from a relation and a lazy
 * proxy to work around (User already carries a @JsonIgnoreProperties for exactly that
 * problem, on an entity that DOES get serialized).
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
