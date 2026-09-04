INSERT INTO permissions (code, resource, action, description) VALUES
    ('ux.review', 'ux', 'review', '批准或退回 UX Task 与 UX 阶段评审')
ON DUPLICATE KEY UPDATE resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'ux.review'
WHERE r.code IN ('OWNER', 'ADMIN', 'PRODUCT', 'UX');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('document.create', 'document.edit', 'document.publish')
WHERE r.code = 'UX';
