-- V160: drop the session operational-lead table (003.2 T143; decided 2026-11-16).
-- The V1 model has no operational lead: responsibility is the Staff assignment only (FR-012c, T124 removed the
-- entity, repository and every read/write). The table is unreferenced; any rows held only historical lead grants.
-- No applied migration is edited.

DROP TABLE IF EXISTS session_operational_leads;
