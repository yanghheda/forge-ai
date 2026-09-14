-- Developer、QA 与 Release Approver 必须能从各自角色入口创建受控 Agent Run。
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'agent.run'
WHERE role.organization_id IS NULL
  AND role.code IN ('DEVELOPER', 'QA', 'RELEASE_APPROVER');
