CREATE TABLE workout_exercises (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    name_de VARCHAR(120) NOT NULL
);
CREATE TABLE workout_plans (
    id UUID PRIMARY KEY,
    owner_id UUID REFERENCES platform_accounts(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    notes VARCHAR(2000) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX workout_plans_owner_idx ON workout_plans(owner_id, name, id);
CREATE TABLE workout_plan_exercises (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES workout_plans(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES workout_exercises(id),
    position INTEGER NOT NULL CHECK (position >= 0),
    sets_min INTEGER NOT NULL CHECK (sets_min BETWEEN 1 AND 100),
    sets_max INTEGER NOT NULL CHECK (sets_max BETWEEN sets_min AND 100),
    mode VARCHAR(12) NOT NULL CHECK (mode IN ('reps', 'seconds', 'unspecified')),
    target_min INTEGER,
    target_max INTEGER,
    rest_min INTEGER,
    rest_max INTEGER,
    superset VARCHAR(32) NOT NULL DEFAULT '',
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    CHECK ((mode = 'unspecified' AND target_min IS NULL AND target_max IS NULL) OR
           (mode <> 'unspecified' AND target_min IS NOT NULL AND target_max IS NOT NULL AND target_min >= 1 AND target_max BETWEEN target_min AND 86400)),
    CHECK ((rest_min IS NULL AND rest_max IS NULL) OR
           (rest_min IS NOT NULL AND rest_max IS NOT NULL AND rest_min >= 0 AND rest_max BETWEEN rest_min AND 86400))
);
CREATE INDEX workout_plan_exercises_plan_idx ON workout_plan_exercises(plan_id, position);
INSERT INTO apps (created_at, updated_at, path, title, description)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'workout', 'Workout', 'Your sessions. Your pace. Build and manage reusable training plans.'
WHERE NOT EXISTS (SELECT 1 FROM apps WHERE path = 'workout');
