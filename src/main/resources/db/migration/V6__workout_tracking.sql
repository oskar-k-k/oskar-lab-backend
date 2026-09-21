ALTER TABLE workout_plan_exercises ADD COLUMN tracking_mode VARCHAR(12) NOT NULL DEFAULT 'reps'
    CHECK (tracking_mode IN ('reps', 'seconds', 'weighted'));
UPDATE workout_plan_exercises SET tracking_mode = 'seconds' WHERE mode = 'seconds';
UPDATE workout_plan_exercises SET tracking_mode = 'seconds' WHERE exercise_id IN (
    SELECT id FROM workout_exercises WHERE name IN ('Human Flag · technique', 'Human Flag Progression', 'Press / L-Sit / Pike → Handstand'));
UPDATE workout_plan_exercises SET tracking_mode = 'weighted' WHERE exercise_id IN (
    SELECT id FROM workout_exercises WHERE name IN ('Weighted Dips', 'Leg Curl', 'Overhead Triceps Extension', 'Lateral Raise',
    'Weighted Pull-up', 'Chest-supported Row', 'Bayesian / Cable Curl', 'Hammer Curl', 'Hack Squat / Squat',
    'Romanian Deadlift', 'Preacher / Cable Curl', 'Triceps Pushdown', 'Hammer / Reverse Curl'));
CREATE TABLE workout_logs (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES platform_accounts(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES workout_exercises(id),
    plan_id UUID NOT NULL,
    plan_version BIGINT NOT NULL,
    position INTEGER NOT NULL,
    plan_name VARCHAR(120) NOT NULL,
    tracking_mode VARCHAR(12) NOT NULL CHECK (tracking_mode IN ('reps', 'seconds', 'weighted')),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX workout_history_idx ON workout_logs(owner_id, exercise_id, recorded_at DESC, id DESC);
CREATE TABLE workout_log_sets (
    log_id UUID NOT NULL REFERENCES workout_logs(id) ON DELETE CASCADE,
    set_number INTEGER NOT NULL CHECK (set_number BETWEEN 1 AND 100),
    actual_value INTEGER NOT NULL CHECK (actual_value BETWEEN 0 AND 86400),
    weight NUMERIC(8,2) CHECK (weight BETWEEN 0 AND 10000),
    PRIMARY KEY (log_id, set_number)
);
