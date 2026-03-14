-- Add created_by to customers for data isolation
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

-- USER role: give same operational permissions as STAFF
INSERT INTO role_permissions (role, permission_id)
SELECT 'USER', id FROM permissions
WHERE name IN (
    'VOUCHER_CREATE', 'VOUCHER_UPDATE', 'VOUCHER_READ',
    'CAMPAIGN_CREATE', 'CAMPAIGN_UPDATE', 'CAMPAIGN_READ',
    'CUSTOMER_CREATE', 'CUSTOMER_UPDATE', 'CUSTOMER_READ',
    'APIKEY_CREATE', 'APIKEY_DEACTIVATE',
    'DISTRIBUTION_CREATE', 'DISTRIBUTION_READ',
    'DASHBOARD_READ'
)
ON CONFLICT DO NOTHING;
