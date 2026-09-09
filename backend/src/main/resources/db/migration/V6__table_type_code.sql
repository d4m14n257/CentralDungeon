-- #197 again, on the last two phrases the application still wrote in one language.
--
-- `table_types` ships with two rows written in English by V2__seed.sql - "Public" and "First class",
-- with their descriptions - and they reached the screen verbatim, so a reader in Spanish got English
-- labels no matter which language they picked. It is the same defect V4 fixed for the notification
-- text and for "Mesa llena": what the application wrote has to follow the reader's language, and
-- only what a person wrote stays verbatim.
--
-- The two are told apart by `code`, not by a guess. A seeded row has one and the frontend renders
-- its translation; a type an admin adds later has none, and its `name` is shown exactly as typed,
-- the same way `systems`, `tags` and `platforms` already work.
--
-- Nullable on purpose, and that is the whole design: `code IS NULL` means "a person named this".

ALTER TABLE table_types
    ADD COLUMN code VARCHAR(32) NULL AFTER name;

ALTER TABLE table_types
    ADD CONSTRAINT uk_table_types_code UNIQUE (code);

UPDATE table_types SET code = 'PUBLIC'      WHERE id = '2eec07aa-aa82-43ed-af44-7a17514fffdd';
UPDATE table_types SET code = 'FIRST_CLASS' WHERE id = '1b04587b-3ed0-47a1-b250-ec0c5b99bfd4';
