-- The server remembers every refresh token it hands out, so a session can be ended on the server.
--
-- A refresh JWT on its own cannot be revoked: logging out only expired the cookie, and a stolen token
-- kept working until it expired. Each row is one issued token; the tokens of one login share a
-- family_id. Renewing revokes the presented token and inserts its successor in the same family;
-- presenting a token that is already revoked is a reuse, and the whole family is revoked; logging out
-- revokes the family too.
--
-- Only a SHA-256 of the token's jti is kept, never the token or the jti itself, so the table holds no
-- usable session. Rows are never updated except to stamp revoked_at, and nothing deletes them yet:
-- purging expired rows is pending.

CREATE TABLE refresh_tokens (
    id         VARCHAR(64) NOT NULL,
    jti_hash   CHAR(64)    NOT NULL,   -- hex SHA-256 of the token's jti
    family_id  VARCHAR(64) NOT NULL,   -- shared by every token of one login
    user_id    VARCHAR(64) NOT NULL,
    expires_at DATETIME    NOT NULL,   -- same as the token's own exp
    revoked_at DATETIME    NULL,       -- rotated, reused or logged out
    created_at DATETIME    NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_jti_hash UNIQUE (jti_hash),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_rt_family ON refresh_tokens (family_id);
CREATE INDEX ix_rt_expires ON refresh_tokens (expires_at);
