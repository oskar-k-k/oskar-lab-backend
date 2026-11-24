ALTER TABLE travel_attachments ADD COLUMN storage_project VARCHAR(64) NOT NULL DEFAULT 'thailand';
CREATE OR REPLACE FUNCTION queue_deleted_travel_file() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.orbit_file_id IS NOT NULL THEN
        INSERT INTO platform_file_deletions(id, project, orbit_file_id)
        VALUES (OLD.id, OLD.storage_project, OLD.orbit_file_id)
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;
