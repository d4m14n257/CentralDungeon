-- F1.4 maps `files` for the first time, and the entity extends BaseEntity - which maps `updated_at`.
--
-- The baseline gave `files` only `created_at` and `deleted_at`. With `ddl-auto: validate` a missing
-- column is not a warning: the application refuses to start. So this is not cosmetic bookkeeping,
-- it is what lets the entity exist at all.
--
-- And the column earns its place: F1.4 mutates a file row four different ways - renaming it,
-- promoting a Single-use into the owner's library (#68), stamping `last_used_at` on every use (#75)
-- and marking it deleted (#25). Every one of those is an update whose moment was going unrecorded.
--
-- Additive and safe on existing rows: NULL means "never updated", which is what BaseEntity already
-- expects and what every other table's `updated_at` holds until its first change.
--
-- `table_files` deliberately gets nothing here. It is a bridge table with a composite key, it does
-- not extend BaseEntity, and it has no id or `updated_at` of its own - same shape as table_systems
-- and table_schedules.

ALTER TABLE files
    ADD COLUMN updated_at DATETIME NULL AFTER created_at;
