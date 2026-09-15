-- F3.1: every role change and every block leaves a row of its own, with the reason (#84, #169).
--
-- `audit_logs` is F6 and `approval_requests` is F3.2, so neither exists yet. Rather than inventing a
-- generic table that F6 would replace, each entity keeps its own trail - which is the precedent the
-- project already set with `table_status_changes`, and these two are copied from it: an immutable
-- row, no updated_at, no deleted_at, never edited and never deleted.
--
-- The one difference with `table_status_changes` is that `justification` is NOT NULL here. There is
-- no role change and no block that does not deny or grant something to a person, so there is no
-- transition that can honestly go without a reason (#84, #169).

CREATE TABLE user_role_changes (
    id            VARCHAR(64) NOT NULL,
    user_id       VARCHAR(64) NOT NULL,
    role_id       VARCHAR(64) NOT NULL,
    action        VARCHAR(32) NOT NULL,   -- 'Granted' | 'Revoked'
    changed_by    VARCHAR(64) NOT NULL,
    justification LONGTEXT    NOT NULL,   -- always required (#169)
    created_at    DATETIME    NOT NULL,
    CONSTRAINT pk_user_role_changes PRIMARY KEY (id),
    CONSTRAINT fk_urc_user       FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_urc_role       FOREIGN KEY (role_id)    REFERENCES roles (id),
    CONSTRAINT fk_urc_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_urc_user ON user_role_changes (user_id, created_at);

CREATE TABLE user_status_changes (
    id            VARCHAR(64) NOT NULL,
    user_id       VARCHAR(64) NOT NULL,
    from_status   VARCHAR(32) NOT NULL,
    to_status     VARCHAR(32) NOT NULL,
    changed_by    VARCHAR(64) NOT NULL,
    justification LONGTEXT    NOT NULL,   -- always required (#84)
    created_at    DATETIME    NOT NULL,
    CONSTRAINT pk_user_status_changes PRIMARY KEY (id),
    CONSTRAINT fk_usc_user       FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_usc_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_usc_user ON user_status_changes (user_id, created_at);
