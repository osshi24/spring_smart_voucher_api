-- Add response_time_ms column to api_request_logs
ALTER TABLE api_request_logs
    ADD COLUMN IF NOT EXISTS response_time_ms BIGINT NULL;
