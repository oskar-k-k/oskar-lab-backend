CREATE TABLE travel_packing_lists (
    trip_id UUID PRIMARY KEY REFERENCES travel_trips(id) ON DELETE CASCADE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
INSERT INTO travel_packing_lists(trip_id, updated_at) SELECT id, CURRENT_TIMESTAMP FROM travel_trips;
CREATE TABLE travel_packing_people (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES travel_packing_lists(trip_id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0)
);
CREATE INDEX travel_packing_people_trip_idx ON travel_packing_people(trip_id, position);
CREATE TABLE travel_packing_groups (
    id UUID PRIMARY KEY,
    person_id UUID NOT NULL REFERENCES travel_packing_people(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0)
);
CREATE INDEX travel_packing_groups_person_idx ON travel_packing_groups(person_id, position);
CREATE TABLE travel_packing_items (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES travel_packing_groups(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    checked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX travel_packing_items_group_idx ON travel_packing_items(group_id, position);
