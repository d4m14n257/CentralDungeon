-- F3.4: the veto and its lifting both leave a row of their own, with the reason (#29, #39).
--
-- WHY A TABLE AND NOT A COLUMN. Two decisions look like they disagree and do not. #29 says `Blocked`
-- «no necesita tabla propia» - true, it is a *state* of `table_registrations` and that is where it
-- lives. #39 asks that «el veto y su levantamiento queden registrados». What is stored here is not
-- the veto: it is the **change**. A `blocked_reason` column can hold the reason somebody was vetoed
-- and can never say that the veto was lifted, by whom, or how many times it has happened - which is
-- precisely what #39 wants visible: «que un patrón de vetos impulsivos sea visible».
--
-- `registration_rejections` already exists and is NOT this table: it is modelled as "a rejection",
-- with no from_status/to_status, so it cannot represent the lifting at all.
--
-- Copied from `table_status_changes` by way of `V11`: an immutable row, no updated_at, no
-- deleted_at, never edited and never deleted. The one difference with `table_status_changes` is the
-- same one V11 made, for the same reason - `justification` is NOT NULL here. A veto with no reason
-- is not reversible in practice (#39), and neither is its lifting answerable.

CREATE TABLE registration_status_changes (
    id              VARCHAR(64) NOT NULL,
    registration_id VARCHAR(64) NOT NULL,
    from_status     VARCHAR(32) NOT NULL,
    to_status       VARCHAR(32) NOT NULL,
    changed_by      VARCHAR(64) NOT NULL,
    justification   LONGTEXT    NOT NULL,   -- always required (#39)
    created_at      DATETIME    NOT NULL,
    CONSTRAINT pk_registration_status_changes PRIMARY KEY (id),
    CONSTRAINT fk_rsc_registration FOREIGN KEY (registration_id) REFERENCES table_registrations (id),
    CONSTRAINT fk_rsc_changed_by   FOREIGN KEY (changed_by)      REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Leads with registration_id and orders by time: the two reads are "what happened to this
-- application, oldest first" and "what did the last change take it away from", which is what
-- lifting a veto needs in order to know where to put the person back.
CREATE INDEX ix_rsc_registration ON registration_status_changes (registration_id, created_at);

-- No ALTER on table_registrations.status: it is already VARCHAR(32) and never MySQL's ENUM type
-- (modelo-datos.md §1), so `Blocked` is a new value of the application's enum and nothing else -
-- the same way adding an approval request type is a constant and never an ALTER (#78).
