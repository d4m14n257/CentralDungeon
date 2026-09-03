-- #197: a notification stores what happened, not the sentence describing it.
--
-- Until now every row froze its text in the language it was written in, which made the language
-- switch a lie the moment somebody looked at their inbox. From here a row carries its type plus the
-- names its sentence needs, and the frontend renders it in whatever language the reader picked.
--
-- Additive and reversible in practice: `title` becomes nullable so new rows can leave it empty, and
-- the rows already written keep their frozen Spanish text. The reader falls back to it when `params`
-- is NULL, so no backfill is needed and no inbox loses history.

ALTER TABLE notifications
    ADD COLUMN params VARCHAR(1024) NULL AFTER message;

ALTER TABLE notifications
    MODIFY COLUMN title VARCHAR(128) NULL;

-- The one system-written rejection reason (#34) stops being prose and becomes a code, for the same
-- reason: the master's own text is theirs and stays verbatim, but "Mesa llena" was written by the
-- application and has to follow the reader's language. `rejected_by IS NULL` is what already tells
-- the two apart, so no column is needed - only the value.
UPDATE registration_rejections
SET description = 'TABLE_FULL'
WHERE rejected_by IS NULL
  AND description = 'Mesa llena';
