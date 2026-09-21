-- Preserve old contextual metadata and all set values; new logs only need owner + exercise.
ALTER TABLE workout_logs ALTER COLUMN plan_id DROP NOT NULL;
ALTER TABLE workout_logs ALTER COLUMN plan_version DROP NOT NULL;
ALTER TABLE workout_logs ALTER COLUMN position DROP NOT NULL;
ALTER TABLE workout_logs ALTER COLUMN plan_name DROP NOT NULL;
