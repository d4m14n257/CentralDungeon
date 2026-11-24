-- A request to open a single table now carries the name the requester proposes for it.
--
-- Approving a TableOpen request creates a Draft table under the requester's name, and the draft needs
-- a name from the start. Only TableOpen requests fill this column; every other type leaves it null.

ALTER TABLE approval_requests
    ADD COLUMN proposed_table_name VARCHAR(128) NULL;  -- the Draft's name, set only for TableOpen
