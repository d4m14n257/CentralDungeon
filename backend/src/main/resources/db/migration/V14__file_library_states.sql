-- The platform's library gets a lifecycle of its own (#282).
--
-- An admin's upload no longer has to be published on the spot: it can sit in the library unpublished
-- (file_type 'Library'), be published ('Public'), and be hidden again - back to 'Library', still in the
-- library, offered to nobody. 'Library' needs no DDL: file_type is a VARCHAR (#10).
--
-- What does need a column is telling "never published" from "hidden": both are 'Library', and the
-- screen names them differently because they are different moments. published_at is when the file was
-- first published, and it is never cleared - hiding a file does not make it never have been published.
ALTER TABLE files ADD COLUMN published_at DATETIME NULL;
