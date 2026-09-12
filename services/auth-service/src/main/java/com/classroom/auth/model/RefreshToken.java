package com.classroom.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "user_id", nullable = false)
    @ToString.Include
    private Long userId;

    // Left out of toString: it is a SHA-256 digest and not the raw secret, but there is no
    // reason to put even that where a log line can show it.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    @ToString.Include
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    @ToString.Include
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    @ToString.Include
    private LocalDateTime revokedAt;
}
