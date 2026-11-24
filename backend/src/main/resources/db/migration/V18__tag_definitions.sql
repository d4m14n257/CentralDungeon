-- A tag group gains a category and a plain-text definition. Both belong to the group: only its
-- canonical entry stores them, and its aliases read them from there. NULL means "not classified yet".
-- The category is a fixed set of codes the application owns; the column only stores the code.

ALTER TABLE tags
    ADD COLUMN category    VARCHAR(32)  NULL,
    ADD COLUMN description VARCHAR(500) NULL;

-- Classify the starting tags seeded in V3__catalog_seed.sql.
UPDATE tags SET category = 'Experience' WHERE id = '5092e308-05ea-4555-a983-f04f0ba03c7a'; -- Principiantes
UPDATE tags SET category = 'PlayStyle'  WHERE id = '54469a32-445f-491f-88d8-cd6eb8008992'; -- Rol
UPDATE tags SET category = 'PlayStyle'  WHERE id = '1e71995b-5367-43c2-bde2-b66a352a69b2'; -- Combate
UPDATE tags SET category = 'Format'     WHERE id = '2287a224-dfa1-4b83-8a00-d56cf5a6909c'; -- One-shot
UPDATE tags SET category = 'Format'     WHERE id = 'c7ec9b0f-5cb9-4193-bbd0-3722860a3eb5'; -- Homebrew
