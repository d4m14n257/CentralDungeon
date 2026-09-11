-- #233: a file's cajón stops being a guess about the document and becomes the flow it belongs to.
--
-- The first attempt at this was a column on `files` holding a document taxonomy - character sheet,
-- handout, template, rules - that the uploader picked from a select. It was wrong twice over.
--
-- Wrong about WHO knows. Nobody uploading can honestly declare "this is a character sheet template":
-- what a master asks for might be a form in a .doc, a spreadsheet, anything. The only thing the
-- system knows for certain is **where the file was uploaded from**, and that is what classifies it.
--
-- Wrong about CARDINALITY, which is the reason this is a table and not a column. A file belongs to
-- every flow it has been used in: a sheet uploaded on an application and later handed in to a task
-- belongs to both, and a column would have to pick one and lie about the other. Deduplication (#75)
-- makes that unavoidable rather than hypothetical - the same bytes are one row by design.
--
-- **Membership is cumulative and never revoked.** This is the part that makes it a stored fact
-- rather than a derived one, and the distinction is easy to miss: the bridge tables answer "where is
-- this file linked right now", while this answers "where has it ever belonged". Detaching a file
-- from a table changes the first and must not touch the second - the file was material for that
-- table, and it stays classified as such. Letting go of the file marks `files.status` and leaves
-- these rows standing as the record.
--
-- It is also what lets a published file share a cajón with a personal one. An admin's blank sheet is
-- attached to no table and handed in to no task, so it has nothing to derive a cajón from - it is
-- published *for* a flow rather than used *in* one. A declared row says so, and the picker can then
-- offer "the community's, for this cajón" above "yours, for this cajón" without knowing which of the
-- two produced each row.

CREATE TABLE file_categories (
    file_id    VARCHAR(64) NOT NULL,
    category   VARCHAR(32) NOT NULL,  -- TableMaterial|MasterRequest|PlayerApplication|PlayerSubmission|Announcement
    created_at DATETIME    NOT NULL,
    CONSTRAINT pk_file_categories PRIMARY KEY (file_id, category),
    CONSTRAINT fk_file_categories_file FOREIGN KEY (file_id) REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The pair is the key, so joining a file to a cajón it already belongs to cannot insert twice. No
-- status column and no `deleted_at`: a row here is only ever added.

-- Browsing one cajón - the published set for a flow, or somebody's own files in it.
CREATE INDEX ix_file_categories_category ON file_categories (category);

-- ---------------------------------------------------------------- public_audience
--
-- Derogates #64 and its M24.1 fix. The audience existed to stop a document written for masters from
-- turning up in front of a player, and the cajón now answers that better than three coarse buckets
-- did: `PlayerApplication` is a player's by construction, `TableMaterial` is a master's. Keeping both
-- would be two axes where one decides, and the second one drifting out of agreement with the first.
--
-- What the audience could say and a flow cannot is "this is for the community at large" - the rules,
-- an announcement. That becomes its own cajón, `Announcement`, which only an admin fills and which
-- is always published.

ALTER TABLE files DROP COLUMN public_audience;

-- ---------------------------------------------------------------- task_files
--
-- The master's half of a request, which the model never had. `table_tasks.accepts_files` says whether
-- an answer may carry files; nothing said the *asking* could. So a master could write "send me your
-- sheet on this form" and had no way to attach the form - the blank the request is about.
--
-- Same shape as `submission_files`, and for the same reason: it links and never copies (#65, #79), so
-- the community's published form is attached by every master that wants it rather than re-uploaded.
-- It is what fills the `MasterRequest` cajón with anything other than what an admin published.

CREATE TABLE task_files (
    task_id    VARCHAR(64) NOT NULL,
    file_id    VARCHAR(64) NOT NULL,
    status     VARCHAR(32) NOT NULL DEFAULT 'Current',
    created_at DATETIME    NOT NULL,
    deleted_at DATETIME    NULL,
    CONSTRAINT pk_task_files PRIMARY KEY (task_id, file_id),
    CONSTRAINT fk_task_files_task FOREIGN KEY (task_id) REFERENCES table_tasks (id),
    CONSTRAINT fk_task_files_file FOREIGN KEY (file_id) REFERENCES files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX ix_task_files_file ON task_files (file_id, status);
