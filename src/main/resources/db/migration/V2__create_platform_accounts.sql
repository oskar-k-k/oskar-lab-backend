CREATE TABLE platform_accounts (
    id UUID PRIMARY KEY,
    username VARCHAR(32) UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(256),
    google_subject VARCHAR(255) UNIQUE,
    terms_accepted_at TIMESTAMP WITH TIME ZONE,
    terms_version VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE platform_sessions (
    digest VARCHAR(64) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES platform_accounts(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX platform_sessions_account_idx ON platform_sessions(account_id);
CREATE INDEX platform_sessions_expiry_idx ON platform_sessions(expires_at);
