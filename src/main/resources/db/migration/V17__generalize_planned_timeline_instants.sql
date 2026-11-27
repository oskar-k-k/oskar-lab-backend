-- Preserve existing stay plans while allowing separate planned instants for every point role.
ALTER TABLE travel_points ADD COLUMN timeline_date DATE;
UPDATE travel_points SET timeline_date = local_date WHERE timeline_time IS NOT NULL;
ALTER TABLE travel_points ADD CONSTRAINT travel_points_timeline_complete CHECK (
    (timeline_time IS NULL AND timeline_date IS NULL AND timeline_utc_offset IS NULL) OR
    (timeline_time IS NOT NULL AND timeline_date IS NOT NULL AND timeline_utc_offset IS NOT NULL)
);
UPDATE travel_points SET time_meaning = CASE
    WHEN local_time IS NULL THEN 'UNSPECIFIED'
    WHEN role = 'CHECK_IN' THEN 'NOT_BEFORE'
    WHEN role = 'CHECK_OUT' THEN 'NOT_AFTER'
    ELSE 'EXACT' END;
ALTER TABLE travel_points ADD CONSTRAINT travel_points_role_time_meaning CHECK (
    time_meaning = CASE
        WHEN local_time IS NULL THEN 'UNSPECIFIED'
        WHEN role = 'CHECK_IN' THEN 'NOT_BEFORE'
        WHEN role = 'CHECK_OUT' THEN 'NOT_AFTER'
        ELSE 'EXACT' END
);
UPDATE travel_entries e SET
    sort_date = COALESCE(p.timeline_date, p.local_date),
    sort_time = COALESCE(p.timeline_time, p.local_time, TIME '23:59:59')
FROM travel_points p WHERE p.entry_id = e.id AND p.position = 0;
