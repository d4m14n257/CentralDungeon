-- F3.5: every change to a system setting leaves a row of its own, with the reason (#141).
--
-- WHY A TABLE AND NOT THE TWO COLUMNS NEXT DOOR. `system_settings` already has `updated_by` and
-- `updated_at` - V1 put them there - and they answer a different question: who has the setting like
-- this *now*. #141 asks that «cada cambio se audita», and a pair of columns the next edit overwrites
-- cannot say a limit was raised on Tuesday and put back on Wednesday, which is exactly the pattern
-- worth being able to see. Same distinction `user_status_changes` makes against `users.status`, and
-- this row is copied from it.
--
-- WHY `setting_key` HAS NO FOREIGN KEY, and why this is not #78 coming back. What it points at is a
-- constant of the `SettingKey` enum, resolved at compile time and validated before anything is
-- written, so it cannot dangle the way a polymorphic `entity_id` can. The constraint is absent for
-- the opposite reason: `system_settings` holds *overrides only* - there is no row for a setting
-- nobody has changed - so the first change to a key legitimately records a key that has no row yet.
--
-- `from_value` IS NULL means "the platform was still on the shipped default", which is a different
-- fact from "it was already this number". Immutable row: no updated_at, no deleted_at, never edited
-- and never deleted.
--
-- NOTHING IS SEEDED HERE, and `system_settings` gets no seed either. The defaults live in
-- `SettingKey`, in code, so a fresh database and a wiped table behave identically and changing a
-- default is never a migration.

CREATE TABLE system_setting_changes (
    id            VARCHAR(64)  NOT NULL,
    setting_key   VARCHAR(64)  NOT NULL,
    from_value    VARCHAR(512) NULL,       -- NULL = it was still the shipped default
    to_value      VARCHAR(512) NOT NULL,
    changed_by    VARCHAR(64)  NOT NULL,
    justification LONGTEXT     NOT NULL,   -- always required (#141)
    created_at    DATETIME     NOT NULL,
    CONSTRAINT pk_system_setting_changes PRIMARY KEY (id),
    CONSTRAINT fk_ssc_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Leads with setting_key and orders by time: the only read is "what happened to this setting, oldest
-- first", which is what the history panel of /admin/settings opens on one row.
CREATE INDEX ix_ssc_setting ON system_setting_changes (setting_key, created_at);
