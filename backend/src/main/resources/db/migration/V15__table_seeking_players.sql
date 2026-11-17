-- A table already in progress can look for players again.
--
-- When a seat frees up while the game is running - a player was vetoed, or somebody left - the masters
-- may want to fill it instead of carrying on short. That is not a new lifecycle status: the table keeps
-- running, its sessions are untouched, and only whether it takes applications changes. So it is a flag,
-- off for every existing table, that the application turns off again when the seats fill up or the
-- table leaves InProgress.
ALTER TABLE game_tables ADD COLUMN seeking_players BOOLEAN NOT NULL DEFAULT FALSE;
