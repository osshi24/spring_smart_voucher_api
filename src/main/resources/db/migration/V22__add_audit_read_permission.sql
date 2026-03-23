-- Add AUDIT_READ permission and assign to ADMIN role
INSERT INTO permissions (name, description)
VALUES ('AUDIT_READ', 'Read audit logs')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions WHERE name = 'AUDIT_READ'
ON CONFLICT DO NOTHING;

-- Also add USAGE_READ permission for /api/v1/usages endpoints if not exists
INSERT INTO permissions (name, description)
VALUES ('USAGE_READ', 'Read voucher usage history')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions WHERE name = 'USAGE_READ'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT 'STAFF', id FROM permissions WHERE name = 'USAGE_READ'
ON CONFLICT DO NOTHING;
