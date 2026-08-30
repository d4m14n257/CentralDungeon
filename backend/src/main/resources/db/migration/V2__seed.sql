-- Minimum seed for the application to function (modelo-datos.md #6).
-- Ids are fixed literals: this is reference data, not application-generated rows.

INSERT INTO roles (id, name, description) VALUES
    ('37500385-b3aa-417b-843d-286357c7a91d', 'Player', 'Can apply to tables and be accepted as a player'),
    ('aba9bbb7-72f4-4d2d-9a85-8abfc3983f07', 'Master', 'Can create and run their own game tables'),
    ('cb0ebf33-e95e-4dc9-b7a3-9a1b7ad42eea', 'Admin', 'Moderates the platform'),
    ('6a3db8c6-48ed-4b5a-aef0-caf221c5c196', 'Owner', 'Platform owner - can do everything Admin can (#67)');

-- Legacy table_types enum values, kept so existing classification is not lost.
-- Admins add the rest from the application.
INSERT INTO table_types (id, name, description, status, created_at) VALUES
    ('2eec07aa-aa82-43ed-af44-7a17514fffdd', 'Public', 'Open table, visible to the whole community', 'Created', UTC_TIMESTAMP()),
    ('1b04587b-3ed0-47a1-b250-ec0c5b99bfd4', 'First class', 'Curated table with stricter requirements', 'Created', UTC_TIMESTAMP());
