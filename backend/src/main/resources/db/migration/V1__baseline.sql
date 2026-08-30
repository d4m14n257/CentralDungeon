SET NAMES utf8mb4;

-- ---------------------------------------------------------------- identity

CREATE TABLE users (
    id               VARCHAR(64)  NOT NULL,
    discord_id       VARCHAR(32)  NOT NULL,
    discord_username VARCHAR(64)  NOT NULL,
    name             VARCHAR(64)  NULL,                    -- display name, set at onboarding (#134)
    karma            INT          NOT NULL DEFAULT 8000,  -- cached projection (#97)
    karma_updated_at DATETIME     NULL,                    -- last recalculation
    country          CHAR(2)      NULL,                    -- ISO 3166-1 alpha-2, set at onboarding (#134)
    status           VARCHAR(32)  NOT NULL DEFAULT 'Allowed',
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NULL,
    deleted_at       DATETIME     NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_discord_id UNIQUE (discord_id),
    CONSTRAINT ck_users_karma CHECK (karma BETWEEN 0 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles (
    id          VARCHAR(64)  NOT NULL,
    name        VARCHAR(32)  NOT NULL,
    description VARCHAR(256) NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users_roles (
    user_id    VARCHAR(64) NOT NULL,
    role_id    VARCHAR(64) NOT NULL,
    status     VARCHAR(32) NOT NULL DEFAULT 'Allowed',
    created_at DATETIME    NOT NULL,
    deleted_at DATETIME    NULL,
    CONSTRAINT pk_users_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_users_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_users_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- game table

CREATE TABLE table_types (
    id          VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(256) NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'Created',
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NULL,
    deleted_at  DATETIME     NULL,
    CONSTRAINT pk_table_types PRIMARY KEY (id),
    CONSTRAINT uk_table_types_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE game_tables (
    id             VARCHAR(64)   NOT NULL,
    table_type_id  VARCHAR(64)   NULL,
    name           VARCHAR(128)  NOT NULL,
    description    LONGTEXT      NULL,
    permitted      LONGTEXT      NULL,
    requirements   LONGTEXT      NULL,   -- rich text (#62)
    start_date     DATETIME      NULL,   -- UTC (#22)
    duration       TIME          NULL,   -- duration of ONE session
    total_sessions INT           NULL,   -- planned number of sessions (#26)
    max_players    INT           NULL,   -- player cap (#24)
    status         VARCHAR(32)   NOT NULL DEFAULT 'Preparation',
    created_by     VARCHAR(64)   NOT NULL, -- master or admin (#72)
    claimed_by     VARCHAR(64)   NULL,     -- admin who reserved the review (#100)
    claimed_at     DATETIME      NULL,
    closed_at      DATETIME      NULL,   -- set when entering Finished or Canceled (#44)
    created_at     DATETIME      NOT NULL,
    updated_at     DATETIME      NULL,
    deleted_at     DATETIME      NULL,
    CONSTRAINT pk_game_tables PRIMARY KEY (id),
    CONSTRAINT fk_game_tables_type    FOREIGN KEY (table_type_id) REFERENCES table_types (id),
    CONSTRAINT fk_game_tables_creator FOREIGN KEY (created_by)    REFERENCES users (id),
    CONSTRAINT fk_game_tables_claimed FOREIGN KEY (claimed_by)    REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_game_tables_status ON game_tables (status);
CREATE INDEX ix_game_tables_type   ON game_tables (table_type_id);
CREATE INDEX ix_game_tables_closed ON game_tables (closed_at);

CREATE TABLE masters (
    game_table_id VARCHAR(64) NOT NULL,
    user_id       VARCHAR(64) NOT NULL,
    master_type   VARCHAR(32) NOT NULL DEFAULT 'Secondary',  -- Primary | Secondary (#71)
    status        VARCHAR(32) NOT NULL DEFAULT 'Created',
    created_at    DATETIME    NOT NULL,
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_masters PRIMARY KEY (game_table_id, user_id),
    CONSTRAINT fk_masters_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_masters_user  FOREIGN KEY (user_id)       REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Exactly one live Primary per table (#73). MySQL has no partial unique
-- indexes, so MasterService enforces this invariant.

CREATE TABLE table_schedules (
    game_table_id VARCHAR(64) NOT NULL,
    weekday       VARCHAR(16) NOT NULL,
    hourtime      TIME        NOT NULL,   -- UTC (#22)
    status        VARCHAR(32) NOT NULL DEFAULT 'Created',
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_table_schedules PRIMARY KEY (game_table_id, weekday, hourtime),
    CONSTRAINT fk_table_schedules_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE table_sessions (
    id              VARCHAR(64) NOT NULL,
    game_table_id   VARCHAR(64) NOT NULL,
    sequence_number INT         NOT NULL,   -- 1..total_sessions
    scheduled_at    DATETIME    NOT NULL,   -- UTC
    status          VARCHAR(32) NOT NULL DEFAULT 'Scheduled', -- Scheduled | Held | Cancelled
    notes           LONGTEXT    NULL,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NULL,
    deleted_at      DATETIME    NULL,
    CONSTRAINT pk_table_sessions PRIMARY KEY (id),
    CONSTRAINT uk_table_sessions UNIQUE (game_table_id, sequence_number),
    CONSTRAINT fk_table_sessions_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_table_sessions_sched ON table_sessions (game_table_id, scheduled_at);

CREATE TABLE session_attendance (
    table_session_id VARCHAR(64) NOT NULL,
    user_id          VARCHAR(64) NOT NULL,
    attendance       VARCHAR(32) NOT NULL DEFAULT 'Unknown', -- Present | Absent | Excused | Unknown
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NULL,
    CONSTRAINT pk_session_attendance PRIMARY KEY (table_session_id, user_id),
    CONSTRAINT fk_session_attendance_session FOREIGN KEY (table_session_id) REFERENCES table_sessions (id),
    CONSTRAINT fk_session_attendance_user    FOREIGN KEY (user_id)          REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Covering index for the historical attendance count (#137): the PK leads with
-- table_session_id, so it cannot serve a lookup by user. This one makes the
-- aggregate index-only — it never touches the rows.
CREATE INDEX ix_session_attendance_user ON session_attendance (user_id, attendance);

CREATE TABLE table_status_changes (
    id            VARCHAR(64)  NOT NULL,
    game_table_id VARCHAR(64)  NOT NULL,
    from_status   VARCHAR(32)  NOT NULL,
    to_status     VARCHAR(32)  NOT NULL,
    changed_by    VARCHAR(64)  NOT NULL,
    justification LONGTEXT     NULL,     -- required for Pause and Canceled (#32)
    created_at    DATETIME     NOT NULL,
    CONSTRAINT pk_table_status_changes PRIMARY KEY (id),
    CONSTRAINT fk_tsc_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_tsc_user  FOREIGN KEY (changed_by)    REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_tsc_table ON table_status_changes (game_table_id, created_at);

-- ---------------------------------------------------------------- player intake

CREATE TABLE table_registrations (
    id            VARCHAR(64) NOT NULL,
    game_table_id VARCHAR(64) NOT NULL,
    user_id       VARCHAR(64) NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'Candidate',
    description   LONGTEXT    NULL,   -- rich text, optional (#62, #69)
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NULL,
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_table_registrations PRIMARY KEY (id),
    CONSTRAINT fk_table_registrations_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_table_registrations_user  FOREIGN KEY (user_id)       REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- NO UNIQUE (game_table_id, user_id): N applications are allowed (#23).
-- At most ONE active row (Candidate or Player) per pair -- enforced by
-- RegistrationService, since MySQL has no partial unique indexes (#28).
CREATE INDEX ix_table_registrations_status ON table_registrations (game_table_id, status);
CREATE INDEX ix_table_registrations_user   ON table_registrations (user_id, status);

CREATE TABLE registration_rejections (
    id              VARCHAR(64) NOT NULL,
    registration_id VARCHAR(64) NOT NULL,
    description     LONGTEXT    NULL,   -- required (#28 rule 5)
    rejected_at     DATETIME    NOT NULL,
    rejected_by     VARCHAR(64) NULL,   -- NULL = automatic rejection because the table filled up (#34)
    CONSTRAINT pk_registration_rejections PRIMARY KEY (id),
    CONSTRAINT fk_rr_registration FOREIGN KEY (registration_id) REFERENCES table_registrations (id),
    CONSTRAINT fk_rr_user         FOREIGN KEY (rejected_by)     REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- table tasks

CREATE TABLE table_tasks (
    id               VARCHAR(64)  NOT NULL,
    game_table_id    VARCHAR(64)  NOT NULL,
    table_session_id VARCHAR(64)  NULL,   -- NULL = not tied to a session (#63)
    audience         VARCHAR(32)  NOT NULL, -- Candidates | Players | Single
    target_user_id   VARCHAR(64)  NULL,   -- only when audience = 'Single'
    title            VARCHAR(128) NOT NULL,
    description      LONGTEXT     NULL,   -- rich text (#62)
    accepts_text     BOOLEAN      NOT NULL DEFAULT TRUE,
    accepts_files    BOOLEAN      NOT NULL DEFAULT TRUE,
    is_mandatory     BOOLEAN      NOT NULL DEFAULT FALSE, -- informational only, does not block (#70)
    due_at           DATETIME     NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'Open',
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NULL,
    deleted_at       DATETIME     NULL,
    CONSTRAINT pk_table_tasks PRIMARY KEY (id),
    CONSTRAINT fk_task_table   FOREIGN KEY (game_table_id)    REFERENCES game_tables (id),
    CONSTRAINT fk_task_session FOREIGN KEY (table_session_id) REFERENCES table_sessions (id),
    CONSTRAINT fk_task_target  FOREIGN KEY (target_user_id)   REFERENCES users (id),
    CONSTRAINT ck_task_accepts CHECK (accepts_text = TRUE OR accepts_files = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_task_table ON table_tasks (game_table_id, audience, status);

CREATE TABLE task_submissions (
    id             VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    user_id        VARCHAR(64) NOT NULL,
    content        LONGTEXT    NULL,   -- rich text (#62)
    status         VARCHAR(32) NOT NULL DEFAULT 'Pending', -- Pending | Submitted (#76)
    submitted_at   DATETIME    NULL,
    created_at     DATETIME    NOT NULL,
    updated_at     DATETIME    NULL,
    deleted_at     DATETIME    NULL,
    CONSTRAINT pk_task_submissions PRIMARY KEY (id),
    CONSTRAINT fk_tsub_task FOREIGN KEY (task_id) REFERENCES table_tasks (id),
    CONSTRAINT fk_tsub_user        FOREIGN KEY (user_id)        REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_tsub_task ON task_submissions (task_id, user_id);

-- ---------------------------------------------------------------- files

CREATE TABLE files (
    id              VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,  -- original filename, metadata only (#80)
    storage_key     VARCHAR(256) NOT NULL,  -- actual name on disk = id (#80)
    content_hash    CHAR(64)     NULL,      -- SHA-256, used for deduplication (#75)
    mime_type       VARCHAR(128) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    file_type       VARCHAR(32)  NOT NULL DEFAULT 'Single-use', -- Public|Private|Single-use (#68)
    public_audience VARCHAR(32)  NULL,      -- Masters|Players|Announcements (#64)
    user_created_id VARCHAR(64)  NOT NULL,
    last_used_at    DATETIME     NULL,      -- drives the unused-file purge (#75)
    status          VARCHAR(32)  NOT NULL DEFAULT 'Current',
    created_at      DATETIME     NOT NULL,
    deleted_at      DATETIME     NULL,
    CONSTRAINT pk_files PRIMARY KEY (id),
    CONSTRAINT uk_files_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_files_user FOREIGN KEY (user_created_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_files_owner    ON files (user_created_id, file_type, status);
CREATE INDEX ix_files_hash     ON files (content_hash);
CREATE INDEX ix_files_lastused ON files (last_used_at);

CREATE TABLE table_files (
    game_table_id   VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    table_file_type VARCHAR(32) NOT NULL DEFAULT 'Preparation', -- Preparation | Session
    is_private      BOOLEAN     NOT NULL DEFAULT FALSE,
    status          VARCHAR(32) NOT NULL DEFAULT 'Current',
    created_at      DATETIME    NOT NULL,
    deleted_at      DATETIME    NULL,
    CONSTRAINT pk_table_files PRIMARY KEY (game_table_id, file_id),
    CONSTRAINT fk_table_files_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_table_files_file  FOREIGN KEY (file_id)       REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE registration_files (
    registration_id VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'Current',
    created_at      DATETIME    NOT NULL,
    deleted_at      DATETIME    NULL,
    CONSTRAINT pk_registration_files PRIMARY KEY (registration_id, file_id),
    CONSTRAINT fk_rf_registration FOREIGN KEY (registration_id) REFERENCES table_registrations (id),
    CONSTRAINT fk_rf_file         FOREIGN KEY (file_id)         REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE submission_files (
    submission_id VARCHAR(64) NOT NULL,
    file_id       VARCHAR(64) NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'Current',
    created_at    DATETIME    NOT NULL,
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_submission_files PRIMARY KEY (submission_id, file_id),
    CONSTRAINT fk_sf_submission FOREIGN KEY (submission_id) REFERENCES task_submissions (id),
    CONSTRAINT fk_sf_file       FOREIGN KEY (file_id)       REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- catalogs

CREATE TABLE systems (
    id           VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    canonical_id VARCHAR(64)  NULL,   -- NULL = this row is the group's canonical entry (#59)
    status       VARCHAR(32)  NOT NULL DEFAULT 'Created',
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NULL,
    deleted_at   DATETIME     NULL,
    CONSTRAINT pk_systems PRIMARY KEY (id),
    CONSTRAINT uk_systems_name UNIQUE (name),
    CONSTRAINT fk_systems_canonical FOREIGN KEY (canonical_id) REFERENCES systems (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_systems_canonical ON systems (canonical_id);

CREATE TABLE tags (
    id           VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    canonical_id VARCHAR(64)  NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'Created',
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NULL,
    deleted_at   DATETIME     NULL,
    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT uk_tags_name UNIQUE (name),
    CONSTRAINT fk_tags_canonical FOREIGN KEY (canonical_id) REFERENCES tags (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_tags_canonical ON tags (canonical_id);

CREATE TABLE platforms (
    id           VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    canonical_id VARCHAR(64)  NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'Created',
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NULL,
    deleted_at   DATETIME     NULL,
    CONSTRAINT pk_platforms PRIMARY KEY (id),
    CONSTRAINT uk_platforms_name UNIQUE (name),
    CONSTRAINT fk_platforms_canonical FOREIGN KEY (canonical_id) REFERENCES platforms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_platforms_canonical ON platforms (canonical_id);

-- Always depth 1: an alias points at the canonical entry, never at another
-- alias (#59). CatalogService enforces it: the target canonical_id must
-- itself have canonical_id NULL.

CREATE TABLE table_systems (
    game_table_id VARCHAR(64) NOT NULL,
    system_id     VARCHAR(64) NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'Used',
    created_at    DATETIME    NOT NULL,
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_table_systems PRIMARY KEY (game_table_id, system_id),
    CONSTRAINT fk_ts_table  FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_ts_system FOREIGN KEY (system_id)     REFERENCES systems (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE table_tags (
    game_table_id VARCHAR(64) NOT NULL,
    tag_id        VARCHAR(64) NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'Used',
    created_at    DATETIME    NOT NULL,
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_table_tags PRIMARY KEY (game_table_id, tag_id),
    CONSTRAINT fk_tt_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_tt_tag   FOREIGN KEY (tag_id)        REFERENCES tags (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE table_platforms (
    game_table_id VARCHAR(64) NOT NULL,
    platform_id   VARCHAR(64) NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'Used',
    created_at    DATETIME    NOT NULL,
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_table_platforms PRIMARY KEY (game_table_id, platform_id),
    CONSTRAINT fk_tp_table    FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_tp_platform FOREIGN KEY (platform_id)   REFERENCES platforms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- comments

-- Draft: the ONLY table that knows the author (#49). On confirmation the
-- anonymous row in `comments` is created, the quota row is written and this
-- row is deleted. On expiry or table cancellation, author_id and content are
-- wiped (#52).
CREATE TABLE comment_drafts (
    id              VARCHAR(64) NOT NULL,
    author_id       VARCHAR(64) NULL,   -- wiped on expiry (#52)
    target_user_id  VARCHAR(64) NOT NULL,
    game_table_id   VARCHAR(64) NOT NULL,
    content         LONGTEXT    NULL,   -- wiped on expiry (#52)
    comment_type    VARCHAR(32) NOT NULL,  -- JJ | JM | MJ  (General lives in system_feedback)
    karma_impact    VARCHAR(32) NOT NULL DEFAULT 'Neutral',
    status          VARCHAR(32) NOT NULL DEFAULT 'Draft', -- Draft | Confirmed | Expired
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NULL,
    deleted_at      DATETIME    NULL,
    CONSTRAINT pk_comment_drafts PRIMARY KEY (id),
    CONSTRAINT fk_cd_author FOREIGN KEY (author_id)      REFERENCES users (id),
    CONSTRAINT fk_cd_target FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT fk_cd_table  FOREIGN KEY (game_table_id)  REFERENCES game_tables (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_cd_author ON comment_drafts (author_id, game_table_id);

-- Confirmed comment: ANONYMOUS. No author and no table, on purpose (#43, M15).
CREATE TABLE comments (
    id                VARCHAR(64) NOT NULL,
    user_commented_id VARCHAR(64) NOT NULL,  -- recipient, always known (#51)
    description       LONGTEXT    NOT NULL,
    comment_type      VARCHAR(32) NOT NULL,  -- JJ | JM | MJ  (General lives in system_feedback)
    karma_impact      VARCHAR(32) NOT NULL DEFAULT 'Neutral',
    status            VARCHAR(32) NOT NULL DEFAULT 'Under review',
    claimed_by        VARCHAR(64) NULL,      -- admin who reserved it for moderation (#100)
    claimed_at        DATETIME    NULL,
    user_reviewed_id  VARCHAR(64) NULL,      -- the admin who moderated it (#51)
    created_at        DATETIME    NOT NULL,  -- (#82)
    reviewed_at       DATETIME    NULL,
    deleted_at        DATETIME    NULL,
    CONSTRAINT pk_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_claimed   FOREIGN KEY (claimed_by)        REFERENCES users (id),
    CONSTRAINT fk_comments_commented FOREIGN KEY (user_commented_id) REFERENCES users (id),
    CONSTRAINT fk_comments_reviewed  FOREIGN KEY (user_reviewed_id)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_comments_commented ON comments (user_commented_id, status);
CREATE INDEX ix_comments_review    ON comments (status, created_at);

-- Anti-spam quota: one comment per author about the same person per table (#35).
-- Stores HMAC(secret, author+target+table), NEVER the plaintext tuple (#82).
-- Intentionally has no foreign keys.
CREATE TABLE comment_quotas (
    quota_token CHAR(64)  NOT NULL,
    created_at  DATETIME  NOT NULL,
    CONSTRAINT pk_comment_quotas PRIMARY KEY (quota_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Feedback about the SYSTEM, not about a person (#91). Anonymous like comments
-- (#93) but simpler: it is created without an author, so it needs none of the
-- draft machinery from #48.
CREATE TABLE system_feedback (
    id         VARCHAR(64) NOT NULL,
    content    LONGTEXT    NOT NULL,
    status     VARCHAR(32) NOT NULL DEFAULT 'New', -- New | Reviewed | Discarded
                                                   -- READ state, not moderation (#95)
    claimed_by VARCHAR(64) NULL,                   -- admin who reserved it (#100)
    claimed_at DATETIME    NULL,
    created_at DATETIME    NOT NULL,
    deleted_at DATETIME    NULL,
    CONSTRAINT pk_system_feedback PRIMARY KEY (id),
    CONSTRAINT fk_sf_claimed FOREIGN KEY (claimed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_system_feedback ON system_feedback (status, created_at);

-- One every 24 real hours (#94). Token is HMAC(secret, user_id + UTC hour),
-- never the user_id. To check, the service computes the tokens for the last 24
-- hourly buckets and looks for any match. The token ROTATES hourly on purpose:
-- a fixed per-user token would be a permanent pseudonym and would allow
-- grouping someone's submissions. Rows are purged after 24 h.
CREATE TABLE feedback_quotas (
    quota_token CHAR(64) NOT NULL,
    created_at  DATETIME NOT NULL,
    CONSTRAINT pk_feedback_quotas PRIMARY KEY (quota_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_feedback_quotas_created ON feedback_quotas (created_at);

-- ---------------------------------------------------------------- cross-cutting

-- Single mechanism for every request that needs approval (#42).
-- Polymorphic reference with no FK: ApprovalService validates entity_id exists,
-- and that check ships with its unit test (#78, #126).
CREATE TABLE approval_requests (
    id              VARCHAR(64)  NOT NULL,
    request_type    VARCHAR(32)  NOT NULL, -- TablePause | PlayerBan | MasterGrant | TableOpen | General (#90)
    entity_type     VARCHAR(32)  NOT NULL, -- game_table | table_registration | user
    entity_id       VARCHAR(64)  NOT NULL,
    requested_by    VARCHAR(64)  NOT NULL,
    justification   LONGTEXT     NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'Pending', -- Pending | Approved | Rejected
    claimed_by      VARCHAR(64)  NULL,  -- admin who reserved this item (#100)
    claimed_at      DATETIME     NULL,  -- auto-released after a timeout (#100)
    resolved_by     VARCHAR(64)  NULL,
    resolution_note LONGTEXT     NULL,
    resolved_at     DATETIME     NULL,
    created_at      DATETIME     NOT NULL,
    deleted_at      DATETIME     NULL,
    CONSTRAINT pk_approval_requests PRIMARY KEY (id),
    CONSTRAINT fk_ar_claimed   FOREIGN KEY (claimed_by)   REFERENCES users (id),
    CONSTRAINT fk_ar_requested FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_ar_resolved  FOREIGN KEY (resolved_by)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_ar_pending ON approval_requests (status, claimed_by, created_at);
CREATE INDEX ix_ar_entity  ON approval_requests (entity_type, entity_id);

CREATE TABLE notifications (
    id                  VARCHAR(64)   NOT NULL,
    user_id             VARCHAR(64)   NOT NULL,
    notification_type   VARCHAR(32)   NOT NULL,
    title               VARCHAR(128)  NOT NULL,
    message             VARCHAR(1024) NULL,
    related_entity_type VARCHAR(32)   NULL,
    related_entity_id   VARCHAR(64)   NULL,
    read_status         VARCHAR(32)   NOT NULL DEFAULT 'Unread',
    created_at          DATETIME      NOT NULL,
    read_at             DATETIME      NULL,
    deleted_at          DATETIME      NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_notifications_inbox ON notifications (user_id, read_status, created_at);

-- "View as" (#140). The admin is stored here and shown ONLY to the Owner: the affected
-- person sees "modificado por un administrador", never a name. Never over Admin or Owner.
-- Declared before audit_logs because audit_logs references it (FK order).
CREATE TABLE impersonation_sessions (
    id             VARCHAR(64)  NOT NULL,
    admin_id       VARCHAR(64)  NOT NULL,
    target_user_id VARCHAR(64)  NOT NULL,
    reason         VARCHAR(255) NOT NULL,  -- mandatory; shown to the target, unlike admin_id
    started_at     DATETIME     NOT NULL,
    ended_at       DATETIME     NULL,      -- auto-closed 30 min after started_at
    CONSTRAINT pk_impersonation_sessions PRIMARY KEY (id),
    CONSTRAINT fk_is_admin  FOREIGN KEY (admin_id)       REFERENCES users (id),
    CONSTRAINT fk_is_target FOREIGN KEY (target_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_impersonation_target ON impersonation_sessions (target_user_id, started_at);

-- NEVER audits `comments` or `comment_drafts`: it would store author and
-- content together and break anonymity (#43).
CREATE TABLE audit_logs (
    id          VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   VARCHAR(64) NOT NULL,
    action      VARCHAR(16) NOT NULL,  -- Create | Update | Delete
    updated_by  VARCHAR(64) NULL,      -- identity the change happened under, not always who typed it
    impersonation_id VARCHAR(64) NULL, -- set when it happened inside a "view as" (#140)
    before_data JSON        NULL,  -- ONLY the columns that changed (#92)
    after_data  JSON        NULL,  -- ONLY the columns that changed (#92)
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT fk_audit_logs_impersonation FOREIGN KEY (impersonation_id)
        REFERENCES impersonation_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Editable configuration (#141). Key-value so adding a setting is not an ALTER TABLE,
-- same reasoning as #10 with ENUMs. Audited like any other entity. NO SECRETS HERE:
-- the HMAC of #94 and every credential stay in the environment.
CREATE TABLE system_settings (
    setting_key VARCHAR(64)  NOT NULL,
    value       VARCHAR(512) NOT NULL,
    value_type  VARCHAR(16)  NOT NULL,  -- Integer | Decimal | Duration | Text | Boolean
    category    VARCHAR(32)  NOT NULL,  -- Business | Limits | Texts
    updated_by  VARCHAR(64)  NULL,
    updated_at  DATETIME     NULL,
    CONSTRAINT pk_system_settings PRIMARY KEY (setting_key),
    CONSTRAINT fk_ss_user FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);
