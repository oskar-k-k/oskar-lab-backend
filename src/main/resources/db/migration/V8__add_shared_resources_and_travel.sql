-- Email ownership is proven by a new verified provider login, never inferred from registration.
ALTER TABLE platform_accounts ADD COLUMN verified_email VARCHAR(254);

CREATE TABLE platform_resources (
    id UUID PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    owner_id UUID NOT NULL REFERENCES platform_accounts(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX platform_resources_owner_idx ON platform_resources(resource_type, owner_id, created_at, id);
CREATE TABLE platform_resource_viewers (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES platform_resources(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL CHECK (email = LOWER(TRIM(email))),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(resource_id, email)
);
CREATE INDEX platform_resource_viewers_email_idx ON platform_resource_viewers(email, resource_id);

CREATE TABLE travel_trips (
    id UUID PRIMARY KEY REFERENCES platform_resources(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    notes VARCHAR(4000) NOT NULL DEFAULT '',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL CHECK (end_date >= start_date),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE travel_entries (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES travel_trips(id) ON DELETE CASCADE,
    type VARCHAR(24) NOT NULL CHECK (type IN ('FLIGHT','BOAT','TRAIN','BUS','TRANSFER','HOTEL','APARTMENT','ACTIVITY','RESTAURANT','TOUR')),
    title VARCHAR(200) NOT NULL,
    notes VARCHAR(4000) NOT NULL DEFAULT '',
    reference VARCHAR(120) NOT NULL DEFAULT '',
    sort_date DATE NOT NULL,
    sort_time TIME NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX travel_entries_trip_idx ON travel_entries(trip_id, sort_date, sort_time, id);
CREATE TABLE travel_points (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES travel_entries(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 1),
    role VARCHAR(24) NOT NULL CHECK (role IN ('DEPARTURE','ARRIVAL','CHECK_IN','CHECK_OUT','START','END')),
    local_date DATE NOT NULL,
    local_time TIME,
    time_zone VARCHAR(64) NOT NULL,
    utc_offset VARCHAR(9),
    time_meaning VARCHAR(24) NOT NULL CHECK (time_meaning IN ('EXACT','NOT_BEFORE','NOT_AFTER','UNSPECIFIED')),
    place_name VARCHAR(200) NOT NULL,
    address VARCHAR(1000) NOT NULL DEFAULT '',
    UNIQUE(entry_id, role),
    CHECK ((time_meaning = 'UNSPECIFIED' AND local_time IS NULL AND utc_offset IS NULL) OR
           (time_meaning <> 'UNSPECIFIED' AND local_time IS NOT NULL AND utc_offset IS NOT NULL))
);
CREATE INDEX travel_points_entry_idx ON travel_points(entry_id, position);
CREATE TABLE travel_attachments (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES travel_entries(id) ON DELETE CASCADE,
    point_role VARCHAR(24),
    filename VARCHAR(200) NOT NULL,
    media_type VARCHAR(40) NOT NULL CHECK (media_type IN ('application/pdf','image/png','image/jpeg')),
    size BIGINT NOT NULL CHECK (size BETWEEN 1 AND 5242880)
);
CREATE INDEX travel_attachments_entry_idx ON travel_attachments(entry_id, id);
CREATE TABLE travel_attachment_contents (
    id UUID PRIMARY KEY REFERENCES travel_attachments(id) ON DELETE CASCADE,
    bytes BYTEA NOT NULL CHECK (octet_length(bytes) BETWEEN 1 AND 5242880)
);
