-- Account deletion cascades bypass Java services; retain cloud deletion intents in that case too.
CREATE FUNCTION queue_deleted_travel_file() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.orbit_file_id IS NOT NULL THEN
        INSERT INTO platform_file_deletions(id, project, orbit_file_id)
        VALUES (OLD.id, 'thailand', OLD.orbit_file_id)
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER travel_attachment_cloud_cleanup
AFTER DELETE ON travel_attachments
FOR EACH ROW EXECUTE FUNCTION queue_deleted_travel_file();
