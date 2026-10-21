# 4.4 DDL baseline

> Parte de la skill `arquitectura` (ADR-01 §5), sección 4 — el modelo de datos. Se movió desde `docs/` en F4.0; su número original lleva ahora el prefijo `4.` (el viejo §5 es §4.5).

Contenido literal de `backend/src/main/resources/db/migration/V1__baseline.sql`.

**Es el baseline literal y no se edita** (regla dura 9): lo que cambió el schema después vive en su
propia migración, y se anota acá para que este documento no mienta por omisión.

| Migración | Qué cambió |
|---|---|
| `V2__seed.sql` · `V3__catalog_seed.sql` | Solo datos: roles, tipos de mesa y el catálogo de la comunidad |
| `V4__notification_params.sql` | `notifications` gana `params VARCHAR(1024) NULL` y su `title` pasa a nulable; el motivo automático de ADR-09 §3 pasa de «Mesa llena» al código `TABLE_FULL` (ADR-23 §3) |
| `V5__files_updated_at.sql` | `files` gana `updated_at DATETIME NULL`. Obligatoria, no cosmética: F1.4 mapea la tabla, la entidad extiende `BaseEntity` —que mapea esa columna— y con `ddl-auto: validate` la aplicación no arranca sin ella. Además F1.4 muta la fila de cuatro formas (renombrar, promover a `Private`, sellar `last_used_at`, dar de baja) y el momento del cambio se estaba perdiendo. `table_files` no la lleva: es puente con clave compuesta y no extiende `BaseEntity` |
| `V6__table_type_code.sql` | `table_types` gana `code VARCHAR(32) NULL UNIQUE` (ADR-23 §3). Las dos filas que siembra `V2__seed.sql` llegaban a la pantalla en inglés sin importar el idioma elegido. Con `code`, una fila sembrada se traduce en el frontend y una que agrega un admin se muestra tal cual la escribió — igual que `systems`, `tags` y `platforms` |
| `V7__slot_duration.sql` | La duración deja de ser de la mesa y pasa a ser de la franja (ADR-08 §1): `table_schedules` gana `duration TIME NULL`, se backfillea desde `game_tables.duration` y esa columna se elimina. Una mesa que juega tres horas entre semana y seis el sábado no tenía forma de decirlo |
| `V8__start_date_is_a_day.sql` | `game_tables.start_date` pasa de `DATETIME` a `DATE` (ADR-07 §4). Ninguna sesión tomaba su hora de ahí, y como instante UTC el día renderizado se corría según dónde se leyera (ADR-03 §6) |
| `V9__file_categories.sql` | Tres cosas, todas de ADR-14 §4 y ADR-15 §3. **`file_categories`**: el cajón de un archivo pasa a ser una relación —pertenece a todos los flujos en los que se usó, y la deduplicación (ADR-13 §3) vuelve eso el caso normal, no un borde—, add-only, nada revoca una pertenencia. **Se elimina `public_audience`**: el flujo ya dice a quién le sirve, y dos ejes decidiendo lo mismo terminan discrepando; los anuncios pasan a ser su propio cajón. **`task_files`**: la mitad del pedido que el modelo nunca tuvo — `accepts_files` decía si una respuesta podía traer archivos, y nada decía que el pedido pudiera |
| `V10__table_draft_status.sql` | El default de `game_tables.status` pasa a `Draft` (ADR-07 §1): la mesa del master nace borrador y no en la cola de revisión. Solo se mueve el default; las filas existentes en `Preparation` quedan como están, porque eso es lo que `Preparation` significa desde entonces |
| `V11__user_admin_changes.sql` | F3.1: **`user_role_changes`** y **`user_status_changes`**, el rastro inmutable de cada rol otorgado o quitado y de cada bloqueo (ADR-05 §7, ADR-06 §1). Copiadas de `table_status_changes`, con `justification` `NOT NULL`: no hay cambio de rol ni bloqueo que pueda ir sin motivo |
| `V12__registration_status_changes.sql` | F3.4: **`registration_status_changes`**, el veto y su levantamiento como filas propias (ADR-09 §4, §5). `Blocked` sigue siendo un estado de `table_registrations`; lo que se guarda acá es el **cambio**, que una columna no puede contar |
| `V13__system_setting_changes.sql` | F3.5: **`system_setting_changes`**, cada cambio de un ajuste con su motivo (ADR-17 §3). `setting_key` sin FK a propósito: `system_settings` guarda solo overrides, y el primer cambio de una clave registra una clave que todavía no tiene fila. `from_value` nulo significa «seguía en el default» |
| `V14__file_library_states.sql` | La biblioteca de la plataforma gana ciclo de vida (ADR-14 §7). `file_type` suma **`Library`** —en la biblioteca, ofrecido a nadie—, que no necesita DDL porque es `VARCHAR` (ADR-03 §5). Lo que sí la necesita es **`published_at DATETIME NULL`**: sin publicar y oculto son los dos `Library`, y la pantalla los nombra distinto; la columna guarda la primera publicación y nunca se borra, porque ocultar un archivo no hace que nunca haya estado publicado |

```sql
SET NAMES utf8mb4;

-- ---------------------------------------------------------------- identity

CREATE TABLE users (
    id               VARCHAR(64)  NOT NULL,
    discord_id       VARCHAR(32)  NOT NULL,
    discord_username VARCHAR(64)  NOT NULL,
    name             VARCHAR(64)  NULL,                    -- display name, set at onboarding (ADR-10 §4)
    karma            INT          NOT NULL DEFAULT 8000,  -- cached projection (ADR-11 §9)
    karma_updated_at DATETIME     NULL,                    -- last recalculation
    country          CHAR(2)      NULL,                    -- ISO 3166-1 alpha-2, set at onboarding (ADR-10 §4)
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
    requirements   LONGTEXT      NULL,   -- rich text (ADR-03 §8)
    start_date     DATETIME      NULL,   -- UTC (ADR-03 §6)
    duration       TIME          NULL,   -- duration of ONE session
    total_sessions INT           NULL,   -- planned number of sessions (ADR-08 §3)
    max_players    INT           NULL,   -- player cap (ADR-07 §5)
    status         VARCHAR(32)   NOT NULL DEFAULT 'Preparation',
    created_by     VARCHAR(64)   NOT NULL, -- master or admin (ADR-06 §7)
    claimed_by     VARCHAR(64)   NULL,     -- admin who reserved the review (ADR-16 §5)
    claimed_at     DATETIME      NULL,
    closed_at      DATETIME      NULL,   -- set when entering Finished or Canceled (ADR-10 §2)
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
    master_type   VARCHAR(32) NOT NULL DEFAULT 'Secondary',  -- Primary | Secondary (ADR-06 §3)
    status        VARCHAR(32) NOT NULL DEFAULT 'Created',
    created_at    DATETIME    NOT NULL,
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_masters PRIMARY KEY (game_table_id, user_id),
    CONSTRAINT fk_masters_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_masters_user  FOREIGN KEY (user_id)       REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Exactly one live Primary per table (ADR-06 §3). MySQL has no partial unique
-- indexes, so MasterService enforces this invariant.

CREATE TABLE table_schedules (
    game_table_id VARCHAR(64) NOT NULL,
    weekday       VARCHAR(16) NOT NULL,
    hourtime      TIME        NOT NULL,   -- UTC (ADR-03 §6)
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

-- Covering index for the historical attendance count (ADR-08 §5): the PK leads with
-- table_session_id, so it cannot serve a lookup by user. This one makes the
-- aggregate index-only — it never touches the rows.
CREATE INDEX ix_session_attendance_user ON session_attendance (user_id, attendance);

CREATE TABLE table_status_changes (
    id            VARCHAR(64)  NOT NULL,
    game_table_id VARCHAR(64)  NOT NULL,
    from_status   VARCHAR(32)  NOT NULL,
    to_status     VARCHAR(32)  NOT NULL,
    changed_by    VARCHAR(64)  NOT NULL,
    justification LONGTEXT     NULL,     -- required for Pause and Canceled (ADR-07 §2)
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
    description   LONGTEXT    NULL,   -- rich text, optional (ADR-03 §8, ADR-09 §2)
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NULL,
    deleted_at    DATETIME    NULL,
    CONSTRAINT pk_table_registrations PRIMARY KEY (id),
    CONSTRAINT fk_table_registrations_table FOREIGN KEY (game_table_id) REFERENCES game_tables (id),
    CONSTRAINT fk_table_registrations_user  FOREIGN KEY (user_id)       REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- NO UNIQUE (game_table_id, user_id): N applications are allowed (ADR-09 §1).
-- At most ONE active row (Candidate or Player) per pair -- enforced by
-- RegistrationService, since MySQL has no partial unique indexes (ADR-09 §1).
CREATE INDEX ix_table_registrations_status ON table_registrations (game_table_id, status);
CREATE INDEX ix_table_registrations_user   ON table_registrations (user_id, status);

CREATE TABLE registration_rejections (
    id              VARCHAR(64) NOT NULL,
    registration_id VARCHAR(64) NOT NULL,
    description     LONGTEXT    NULL,   -- required (ADR-09 §1 rule 5)
    rejected_at     DATETIME    NOT NULL,
    rejected_by     VARCHAR(64) NULL,   -- NULL = automatic rejection because the table filled up (ADR-09 §3)
    CONSTRAINT pk_registration_rejections PRIMARY KEY (id),
    CONSTRAINT fk_rr_registration FOREIGN KEY (registration_id) REFERENCES table_registrations (id),
    CONSTRAINT fk_rr_user         FOREIGN KEY (rejected_by)     REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- table tasks

CREATE TABLE table_tasks (
    id               VARCHAR(64)  NOT NULL,
    game_table_id    VARCHAR(64)  NOT NULL,
    table_session_id VARCHAR(64)  NULL,   -- NULL = not tied to a session (ADR-15 §1)
    audience         VARCHAR(32)  NOT NULL, -- Candidates | Players | Single
    target_user_id   VARCHAR(64)  NULL,   -- only when audience = 'Single'
    title            VARCHAR(128) NOT NULL,
    description      LONGTEXT     NULL,   -- rich text (ADR-03 §8)
    accepts_text     BOOLEAN      NOT NULL DEFAULT TRUE,
    accepts_files    BOOLEAN      NOT NULL DEFAULT TRUE,
    is_mandatory     BOOLEAN      NOT NULL DEFAULT FALSE, -- informational only, does not block (ADR-15 §2)
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
    content        LONGTEXT    NULL,   -- rich text (ADR-03 §8)
    status         VARCHAR(32) NOT NULL DEFAULT 'Pending', -- Pending | Submitted (ADR-15 §2)
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
    name            VARCHAR(256) NOT NULL,  -- original filename, metadata only (ADR-13 §6)
    storage_key     VARCHAR(256) NOT NULL,  -- actual name on disk = id (ADR-13 §6)
    content_hash    CHAR(64)     NULL,      -- SHA-256, used for deduplication (ADR-13 §3)
    mime_type       VARCHAR(128) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    file_type       VARCHAR(32)  NOT NULL DEFAULT 'Single-use', -- Public|Private|Single-use (ADR-13 §2)
    public_audience VARCHAR(32)  NULL,      -- Masters|Players|Announcements (ADR-13 §2)
    user_created_id VARCHAR(64)  NOT NULL,
    last_used_at    DATETIME     NULL,      -- drives the unused-file purge (ADR-13 §3)
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
    canonical_id VARCHAR(64)  NULL,   -- NULL = this row is the group's canonical entry (ADR-12 §2)
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
-- alias (ADR-12 §2). CatalogService enforces it: the target canonical_id must
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

-- Draft: the ONLY table that knows the author (ADR-11 §6). On confirmation the
-- anonymous row in `comments` is created, the quota row is written and this
-- row is deleted. On expiry or table cancellation, author_id and content are
-- wiped (ADR-11 §6).
CREATE TABLE comment_drafts (
    id              VARCHAR(64) NOT NULL,
    author_id       VARCHAR(64) NULL,   -- wiped on expiry (ADR-11 §6)
    target_user_id  VARCHAR(64) NOT NULL,
    game_table_id   VARCHAR(64) NOT NULL,
    content         LONGTEXT    NULL,   -- wiped on expiry (ADR-11 §6)
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

-- Confirmed comment: ANONYMOUS. No author and no table, on purpose (ADR-11 §5, M15).
CREATE TABLE comments (
    id                VARCHAR(64) NOT NULL,
    user_commented_id VARCHAR(64) NOT NULL,  -- recipient, always known (ADR-11 §7)
    description       LONGTEXT    NOT NULL,
    comment_type      VARCHAR(32) NOT NULL,  -- JJ | JM | MJ  (General lives in system_feedback)
    karma_impact      VARCHAR(32) NOT NULL DEFAULT 'Neutral',
    status            VARCHAR(32) NOT NULL DEFAULT 'Under review',
    claimed_by        VARCHAR(64) NULL,      -- admin who reserved it for moderation (ADR-16 §5)
    claimed_at        DATETIME    NULL,
    user_reviewed_id  VARCHAR(64) NULL,      -- the admin who moderated it (ADR-11 §7)
    created_at        DATETIME    NOT NULL,  -- (ADR-11 §5)
    reviewed_at       DATETIME    NULL,
    deleted_at        DATETIME    NULL,
    CONSTRAINT pk_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_claimed   FOREIGN KEY (claimed_by)        REFERENCES users (id),
    CONSTRAINT fk_comments_commented FOREIGN KEY (user_commented_id) REFERENCES users (id),
    CONSTRAINT fk_comments_reviewed  FOREIGN KEY (user_reviewed_id)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_comments_commented ON comments (user_commented_id, status);
CREATE INDEX ix_comments_review    ON comments (status, created_at);

-- Anti-spam quota: one comment per author about the same person per table (ADR-11 §2).
-- Stores HMAC(secret, author+target+table), NEVER the plaintext tuple (ADR-11 §5).
-- Intentionally has no foreign keys.
CREATE TABLE comment_quotas (
    quota_token CHAR(64)  NOT NULL,
    created_at  DATETIME  NOT NULL,
    CONSTRAINT pk_comment_quotas PRIMARY KEY (quota_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Feedback about the SYSTEM, not about a person (ADR-11 §11). Anonymous like comments
-- (ADR-11 §11) but simpler: it is created without an author, so it needs none of the
-- draft machinery from ADR-11 §3.
CREATE TABLE system_feedback (
    id         VARCHAR(64) NOT NULL,
    content    LONGTEXT    NOT NULL,
    status     VARCHAR(32) NOT NULL DEFAULT 'New', -- New | Reviewed | Discarded
                                                   -- READ state, not moderation (ADR-11 §11)
    claimed_by VARCHAR(64) NULL,                   -- admin who reserved it (ADR-16 §5)
    claimed_at DATETIME    NULL,
    created_at DATETIME    NOT NULL,
    deleted_at DATETIME    NULL,
    CONSTRAINT pk_system_feedback PRIMARY KEY (id),
    CONSTRAINT fk_sf_claimed FOREIGN KEY (claimed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_system_feedback ON system_feedback (status, created_at);

-- One every 24 real hours (ADR-11 §12). Token is HMAC(secret, user_id + UTC hour),
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

-- Single mechanism for every request that needs approval (ADR-16 §1).
-- Polymorphic reference with no FK: ApprovalService validates entity_id exists,
-- and that check ships with its unit test (ADR-16 §2).
CREATE TABLE approval_requests (
    id              VARCHAR(64)  NOT NULL,
    request_type    VARCHAR(32)  NOT NULL, -- TablePause | PlayerBan | MasterGrant | TableOpen | General (ADR-16 §1)
    entity_type     VARCHAR(32)  NOT NULL, -- game_table | table_registration | user
    entity_id       VARCHAR(64)  NOT NULL,
    requested_by    VARCHAR(64)  NOT NULL,
    justification   LONGTEXT     NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'Pending', -- Pending | Approved | Rejected
    claimed_by      VARCHAR(64)  NULL,  -- admin who reserved this item (ADR-16 §5)
    claimed_at      DATETIME     NULL,  -- auto-released after a timeout (ADR-16 §5)
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

-- "View as" (ADR-06 §9). The admin is stored here and shown ONLY to the Owner: the affected
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
-- content together and break anonymity (ADR-11 §5).
CREATE TABLE audit_logs (
    id          VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   VARCHAR(64) NOT NULL,
    action      VARCHAR(16) NOT NULL,  -- Create | Update | Delete
    updated_by  VARCHAR(64) NULL,      -- identity the change happened under, not always who typed it
    impersonation_id VARCHAR(64) NULL, -- set when it happened inside a "view as" (ADR-06 §9)
    before_data JSON        NULL,  -- ONLY the columns that changed (ADR-17 §1)
    after_data  JSON        NULL,  -- ONLY the columns that changed (ADR-17 §1)
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT fk_audit_logs_impersonation FOREIGN KEY (impersonation_id)
        REFERENCES impersonation_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Editable configuration (ADR-17 §3). Key-value so adding a setting is not an ALTER TABLE,
-- same reasoning as ADR-03 §5 with ENUMs. Audited like any other entity. NO SECRETS HERE:
-- the HMAC of ADR-11 §12 and every credential stay in the environment.
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
```

## 4.6 Seed mínimo

Va en `V2__seed.sql`. Sin esto la aplicación no funciona.

- `roles`: **`Player`, `Master`, `Admin`, `Owner`** — cuatro, no tres (ADR-06 §1).
- `table_types`: `Public` / `PUBLIC` y `First class` / `FIRST_CLASS` — los valores del enum heredado, para no perder la clasificación existente. Llevan `code` porque los trajo la aplicación y por eso se traducen en el frontend (ADR-23 §4); los que agreguen los admins desde la aplicación **no lo llevan**, y su nombre se lee tal cual lo escribieron, igual que en `systems`, `tags` y `platforms`.
