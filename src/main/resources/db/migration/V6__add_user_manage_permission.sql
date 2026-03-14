-- Add USER_MANAGE permission if it doesn't exist
INSERT INTO permissions (name, description)
SELECT 'USER_MANAGE', 'Manage user accounts (update details, change role)'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'USER_MANAGE');

-- Assign USER_MANAGE to ADMIN role
INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', p.id
FROM permissions p
WHERE p.name = 'USER_MANAGE'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role = 'ADMIN' AND rp.permission_id = p.id
  );
