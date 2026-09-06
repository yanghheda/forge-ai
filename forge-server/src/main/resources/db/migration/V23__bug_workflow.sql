CREATE TABLE bug_details (
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT 'BUG 工作项标识并作为详情主键',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '详情所属工作区，用于租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '详情所属项目，用于项目授权',
    requirement_id BIGINT UNSIGNED NOT NULL COMMENT '发现缺陷的 Requirement 标识',
    test_run_id BIGINT UNSIGNED NULL COMMENT '发现缺陷的 Test Run；人工草稿可为空',
    test_result_id BIGINT UNSIGNED NULL COMMENT 'FAIL 或 BLOCKED 的 Test Result；人工草稿可为空',
    reproduction_steps_json JSON NOT NULL COMMENT '按顺序保存的非空复现步骤',
    expected_result TEXT NOT NULL COMMENT '测试期望结果',
    actual_result TEXT NOT NULL COMMENT '触发缺陷的实际结果',
    fix_note TEXT NULL COMMENT '研发解决时填写的修复说明；未解决时为空',
    fix_evidence_json JSON NULL COMMENT '关联 MR、Commit 等修复证据；未解决时为空',
    verified_by BIGINT UNSIGNED NULL COMMENT '执行独立验证的 QA 或管理员；未验证时为空',
    verified_at DATETIME(6) NULL COMMENT '最近一次验证 UTC 时间；未验证或 reopen 后为空',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '详情并发修改使用的乐观锁版本',
    PRIMARY KEY (work_item_id),
    KEY idx_bug_details_requirement (requirement_id),
    KEY idx_bug_details_result (test_result_id),
    CONSTRAINT fk_bug_details_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_bug_details_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_bug_details_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_bug_details_requirement FOREIGN KEY (requirement_id) REFERENCES work_items (id),
    CONSTRAINT fk_bug_details_run FOREIGN KEY (test_run_id) REFERENCES test_runs (id),
    CONSTRAINT fk_bug_details_result FOREIGN KEY (test_result_id) REFERENCES test_results (id),
    CONSTRAINT fk_bug_details_verifier FOREIGN KEY (verified_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Bug 的复现、测试来源、修复证据和独立验证事实';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('bug.read', 'bug', 'read', '读取项目范围内的 Bug'),
    ('bug.create', 'bug', 'create', '基于失败测试或人工发现创建 Bug 草稿'),
    ('bug.edit', 'bug', 'edit', '编辑或取消项目范围内的 Bug'),
    ('bug.resolve', 'bug', 'resolve', '开始修复并提交带 MR 或 Commit 的修复证据'),
    ('bug.verify', 'bug', 'verify', '由 QA 或管理员独立验证、关闭或重开 Bug')
ON DUPLICATE KEY UPDATE
    resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'ADMIN') AND p.code LIKE 'bug.%';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'QA' AND p.code IN ('bug.read', 'bug.create', 'bug.edit', 'bug.verify');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'DEVELOPER' AND p.code IN ('bug.read', 'bug.edit', 'bug.resolve');
