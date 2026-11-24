ALTER TABLE travel_attachments ADD COLUMN orbit_file_id UUID;
ALTER TABLE travel_attachments ADD COLUMN sha256 VARCHAR(64);
CREATE TABLE platform_file_deletions (
    id UUID PRIMARY KEY,
    project VARCHAR(64) NOT NULL,
    orbit_file_id UUID NOT NULL,
    UNIQUE(project, orbit_file_id)
);
-- Legacy bytea rows are retained only until explicit verified migration succeeds.
