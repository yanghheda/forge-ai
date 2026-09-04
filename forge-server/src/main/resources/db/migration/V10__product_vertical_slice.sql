ALTER TABLE documents
    ADD UNIQUE KEY uq_documents_work_item_type (work_item_id, type);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'document.read'
WHERE r.code IN ('UX', 'DEVELOPER', 'QA', 'RELEASE_APPROVER');
