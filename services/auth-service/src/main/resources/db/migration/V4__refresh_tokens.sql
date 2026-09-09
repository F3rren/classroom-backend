-- ============================================================================
-- Refresh tokens: the one piece of server-side state a stateless JWT cannot do
-- without, for exactly two things - handing out a new access token without a
-- fresh login, and revoking one early (logout).
--
-- Lives in this database and not in a shared one on purpose: this table is the
-- business of whoever issues tokens, same as jwt.secret itself. No other
-- service ever needs to see it, unlike users, which the same three services
-- verify tokens against.
--
-- ON DELETE CASCADE matters here specifically: unlike the cross-service links
-- elsewhere in this project (which have none, because the other table lives in
-- a different database), user_id and users both live here, so a real foreign
-- key applies - and without the cascade, deleting a user with an outstanding
-- refresh token would fail on the constraint instead of succeeding.
--
-- token_hash, not the token itself: this table is what an attacker who reads
-- this database would want most, and a SHA-256 hash of a 256-bit random value
-- is useless to them without the original, the same reasoning that keeps
-- password in users hashed rather than in clear.
-- ============================================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    created_at timestamp NOT NULL,
    expires_at timestamp NOT NULL,
    revoked_at timestamp
);

-- token_hash's own UNIQUE constraint already gives that lookup an index; what
-- is missing is one for "every token belonging to this user", which nothing
-- here needs yet but the foreign key itself benefits from (Postgres does not
-- index a foreign key column on its own).
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens (user_id);
