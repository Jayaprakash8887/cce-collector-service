-- H2 compatibility: map PostgreSQL JSONB type to TEXT for testing
CREATE DOMAIN IF NOT EXISTS JSONB AS TEXT;
