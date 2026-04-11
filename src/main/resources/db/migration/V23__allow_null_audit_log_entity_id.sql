-- Allow null entity_id in audit_logs for actions like REDEEM that don't have a direct entity ID
ALTER TABLE audit_logs ALTER COLUMN entity_id DROP NOT NULL;
