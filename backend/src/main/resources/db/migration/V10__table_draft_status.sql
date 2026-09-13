-- The master's table is born a draft, not already in the review queue (#245).
--
-- Only the column default moves: `status` is a VARCHAR and the new value needs no schema change.
-- Existing rows are left alone on purpose - a table sitting in `Preparation` today *is* waiting for
-- an admin, which is exactly what `Preparation` means from now on.
ALTER TABLE game_tables
    ALTER COLUMN status SET DEFAULT 'Draft';
