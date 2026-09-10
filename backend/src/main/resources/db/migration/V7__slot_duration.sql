-- #228: how long a session lasts stops being a property of the table and becomes one of the slot.
--
-- The agenda could already say a table plays Tuesday and Saturday, but both had to last the same,
-- because the length lived in `game_tables.duration`. A table that runs three hours midweek and six
-- on a Saturday afternoon had no way to say so, and the master had to choose which of the two to
-- lie about.
--
-- Nothing else read that column: sessions store an instant and not a length (#33), so materializing
-- a calendar is untouched. What it fed was #178 - the interval every clash is measured in - and that
-- now takes each slot's own duration.
--
-- Backfilled and then dropped in one migration: leaving `game_tables.duration` behind as a column
-- nothing reads would be a worse outcome than the change itself.

ALTER TABLE table_schedules
    ADD COLUMN duration TIME NULL AFTER hourtime;

-- Every live slot inherits what its table used to say. A table that never set a duration leaves its
-- slots null, which is exactly what they meant before: an agenda that commits nobody to anything.
UPDATE table_schedules s
    JOIN game_tables t ON t.id = s.game_table_id
SET s.duration = t.duration
WHERE t.duration IS NOT NULL;

ALTER TABLE game_tables
    DROP COLUMN duration;
