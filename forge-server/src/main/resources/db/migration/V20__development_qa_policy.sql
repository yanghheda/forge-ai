ALTER TABLE project_policies
    ADD COLUMN ci_required BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '提交 QA 前是否强制每个 Dev Task 的 MR 当前 head Pipeline 成功；无仓库时不放行'
        AFTER allow_skip_ux;

INSERT INTO permissions (code, resource, action, description) VALUES
    ('development.submit', 'development', 'submit', '在确定性研发与 CI Guard 通过后提交 Requirement 到 QA')
ON DUPLICATE KEY UPDATE
    resource = VALUES(resource),
    action = VALUES(action),
    description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'development.submit'
WHERE r.code IN ('OWNER', 'ADMIN', 'DEVELOPER');
