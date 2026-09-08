package com.classroom.security;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * Builds the signing key from the configured secret, accepting either base64 alphabet.
 *
 * It exists because of a real and recurring defect: the code decoded as base64URL, which uses
 * '-' and '_', while the documentation said everywhere to generate the secret with
 *
 *     openssl rand -base64 48
 *
 * which emits STANDARD base64, with '+' and '/'. Over 64 random characters the probability of
 * meeting neither of those is about 13%: the documented command produced an unusable secret
 * nearly nine times out of ten, and the error message ("Illegal base64url character: '/'")
 * told nobody that the problem was how the secret had been generated.
 *
 * Normalising rather than picking one alphabet is the right answer because a secret is
 * something a person pastes: demanding that they know which of the two variants is wanted is
 * a requirement you cannot enforce, and one that fails obscurely.
 *
 * It MUST live in exactly one place: the signer (auth-service) and the verifiers (everybody
 * else) have to derive the identical key from the same secret. Two slightly different
 * normalisations would produce tokens nobody can validate.
 */
public final class JwtKey {

    private JwtKey() {
    }

    public static SecretKey from(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret non configurato: impostare JWT_SECRET nell'ambiente o in .env");
        }
        // Everything is brought to base64url, the alphabet the decoder expects. A secret
        // already in base64url passes through this line unchanged.
        String normalized = secret.trim().replace('+', '-').replace('/', '_');
        return Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(normalized));
    }
}
