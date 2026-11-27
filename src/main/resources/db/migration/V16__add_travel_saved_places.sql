CREATE TABLE travel_saved_places (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES travel_trips(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    url VARCHAR(2048) NOT NULL DEFAULT '',
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX travel_saved_places_trip_idx ON travel_saved_places(trip_id, created_at DESC, id DESC);
