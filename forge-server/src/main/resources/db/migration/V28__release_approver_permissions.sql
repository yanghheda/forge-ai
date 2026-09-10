INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'RELEASE_APPROVER'
  AND p.code IN ('release.read', 'approval.decide');
