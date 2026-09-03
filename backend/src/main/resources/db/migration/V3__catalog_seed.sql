-- Starting catalog: the systems, platforms and tags the community already uses, with the synonym
-- groups that keep a table findable under any of its names (#54, #56, #59).
--
-- Data, not schema: V1__baseline.sql already created these three tables. Ids are fixed literals
-- because this is reference data, same as V2__seed.sql - not rows the application generated.
--
-- Everything here is born Accepted: an admin looked at this list, which is exactly what the status
-- means (#57). Anything a master proposes from the wizard is born Created instead, and shows to
-- nobody until an admin accepts and classifies it (#55).
--
-- Whatever is missing is added from /admin/catalogs, not by editing this file: an applied migration
-- is never edited (regla dura 9).

-- ----------------------------------------------------------------- systems
-- Canonical entries first: an alias's canonical_id is a real FK, so its target has to exist.

INSERT INTO systems (id, name, canonical_id, status, created_at) VALUES
    ('2e5e8e90-104e-4ba7-b6dc-04104a2de237', 'D&D 5e',           NULL, 'Accepted', UTC_TIMESTAMP()),
    ('cfb6f0af-af0a-49fe-bac8-23ef7b3355ee', 'Pathfinder 2e',    NULL, 'Accepted', UTC_TIMESTAMP()),
    ('f0bfdfec-0635-469a-8093-ee212c82dc78', 'Call of Cthulhu',  NULL, 'Accepted', UTC_TIMESTAMP());

-- The aliases. Depth is always 1: every one of these points at a canonical entry above, never at
-- another alias (#59). Searching "DANDD" and searching "D&D 5e" return the same tables (#54), and
-- each table still shows the name its master picked (#58).
INSERT INTO systems (id, name, canonical_id, status, created_at) VALUES
    ('7b1030c8-a230-41fe-98c6-353a618c37e3', 'D&D',                '2e5e8e90-104e-4ba7-b6dc-04104a2de237', 'Accepted', UTC_TIMESTAMP()),
    ('b9e09c1f-b513-47f5-b1c0-6b99e953ff3a', 'DND',                '2e5e8e90-104e-4ba7-b6dc-04104a2de237', 'Accepted', UTC_TIMESTAMP()),
    ('3e73724c-2265-4811-a775-220f95ebc455', 'DANDD',              '2e5e8e90-104e-4ba7-b6dc-04104a2de237', 'Accepted', UTC_TIMESTAMP()),
    ('79d5390a-9a13-4860-a738-d239fda851fc', 'Dungeons & Dragons', '2e5e8e90-104e-4ba7-b6dc-04104a2de237', 'Accepted', UTC_TIMESTAMP()),
    ('6cc86108-d9cc-475c-b60a-f50559c2119c', 'PF2e',               'cfb6f0af-af0a-49fe-bac8-23ef7b3355ee', 'Accepted', UTC_TIMESTAMP()),
    ('7cdcf55e-c607-4936-9c24-ad1271780ccc', 'CoC',                'f0bfdfec-0635-469a-8093-ee212c82dc78', 'Accepted', UTC_TIMESTAMP());

-- --------------------------------------------------------------- platforms

INSERT INTO platforms (id, name, canonical_id, status, created_at) VALUES
    ('f36eead8-4ecd-4318-ace6-48d5bcd38b09', 'Discord',     NULL, 'Accepted', UTC_TIMESTAMP()),
    ('755204c9-3e6c-41d7-b0ab-5bad4726dbea', 'Roll20',      NULL, 'Accepted', UTC_TIMESTAMP()),
    ('e34c6f38-0896-440d-b1fe-18066bc1ac87', 'Foundry VTT', NULL, 'Accepted', UTC_TIMESTAMP()),
    ('b502b2fc-0adf-40d6-93e3-3692b6cd7289', 'Presencial',  NULL, 'Accepted', UTC_TIMESTAMP());

INSERT INTO platforms (id, name, canonical_id, status, created_at) VALUES
    ('9133ac39-7010-4724-b0c7-92f1ed3ca9ea', 'Mesa física', 'b502b2fc-0adf-40d6-93e3-3692b6cd7289', 'Accepted', UTC_TIMESTAMP());

-- -------------------------------------------------------------------- tags
-- No synonym groups here on purpose: tags are free-form and the ones worth merging only show up
-- once masters start writing them. That is what /admin/catalogs is for.

INSERT INTO tags (id, name, canonical_id, status, created_at) VALUES
    ('5092e308-05ea-4555-a983-f04f0ba03c7a', 'Principiantes', NULL, 'Accepted', UTC_TIMESTAMP()),
    ('54469a32-445f-491f-88d8-cd6eb8008992', 'Rol',           NULL, 'Accepted', UTC_TIMESTAMP()),
    ('1e71995b-5367-43c2-bde2-b66a352a69b2', 'Combate',       NULL, 'Accepted', UTC_TIMESTAMP()),
    ('2287a224-dfa1-4b83-8a00-d56cf5a6909c', 'One-shot',      NULL, 'Accepted', UTC_TIMESTAMP()),
    ('c7ec9b0f-5cb9-4193-bbd0-3722860a3eb5', 'Homebrew',      NULL, 'Accepted', UTC_TIMESTAMP());
