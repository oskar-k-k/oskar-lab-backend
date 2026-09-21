ALTER TABLE platform_accounts ADD COLUMN username_key VARCHAR(32);
UPDATE platform_accounts SET username_key = lower(username) WHERE username IS NOT NULL;
ALTER TABLE platform_accounts ADD CONSTRAINT platform_accounts_username_key_unique UNIQUE (username_key);
