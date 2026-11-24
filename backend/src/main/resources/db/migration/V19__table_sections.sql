-- A table gains four optional sections of rich text, each with a heading of its own on the detail:
-- how characters are created, what to expect, what equipment is needed, and safety and content
-- warnings. NULL means the master did not write that section. Like the description, the service
-- sanitizes each one before it is stored.

ALTER TABLE game_tables
    ADD COLUMN character_creation LONGTEXT NULL,
    ADD COLUMN what_to_expect     LONGTEXT NULL,
    ADD COLUMN equipment          LONGTEXT NULL,
    ADD COLUMN safety             LONGTEXT NULL;
