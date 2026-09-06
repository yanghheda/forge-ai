CREATE TABLE releases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Release Candidate 递增标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '发布所属工作区，用于租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '发布所属项目，用于项目授权',
    version_name VARCHAR(128) NOT NULL COMMENT '项目与环境内唯一的发布版本名',
    environment VARCHAR(64) NOT NULL COMMENT '候选发布的目标环境；本会话不触发部署',
    status VARCHAR(24) NOT NULL COMMENT '发布状态：DRAFT 或 PRECHECKED',
    release_note TEXT NULL COMMENT '人工或 Agent 生成并可编辑的 Release Note；未填写时为空',
    release_note_document_id BIGINT UNSIGNED NULL COMMENT '可选的不可变 Release Note 文档引用；未关联时为空',
    policy_snapshot_json JSON NOT NULL COMMENT '创建时固化的发布检查策略',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建 Release Candidate 的用户标识',
    created_at DATETIME(6) NOT NULL COMMENT 'Release Candidate 创建 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT 'Release Candidate 最近修改 UTC 时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Release Candidate 并发修改与快照失效判断版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_releases_project_version_environment (project_id, version_name, environment),
    KEY idx_releases_workspace_project (workspace_id, project_id),
    CONSTRAINT fk_releases_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_releases_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_releases_note_document FOREIGN KEY (release_note_document_id) REFERENCES documents (id),
    CONSTRAINT fk_releases_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '独立聚合交付资产、检查策略与版本的 Release Candidate';

CREATE TABLE release_items (
    release_id BIGINT UNSIGNED NOT NULL COMMENT 'Release Candidate 标识',
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT '纳入本次发布的 Requirement 标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '关联所属工作区，用于租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '关联所属项目，用于项目隔离',
    PRIMARY KEY (release_id, work_item_id),
    KEY idx_release_items_scope (workspace_id, project_id),
    CONSTRAINT fk_release_items_release FOREIGN KEY (release_id) REFERENCES releases (id),
    CONSTRAINT fk_release_items_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_release_items_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_release_items_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Release Candidate 纳入的 Requirement 集合';

CREATE TABLE release_prechecks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '一次不可变 Precheck 快照标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '快照所属工作区，用于租户隔离',
    release_id BIGINT UNSIGNED NOT NULL COMMENT '被检查的 Release Candidate 标识',
    status VARCHAR(16) NOT NULL COMMENT '六项规则聚合结论：PASS 或 FAIL',
    checks_json JSON NOT NULL COMMENT '按规则名保存的确定性结论与失败事实',
    checked_at DATETIME(6) NOT NULL COMMENT '规则执行并固化快照的 UTC 时间',
    checked_by_type VARCHAR(16) NOT NULL COMMENT '检查发起者类型：USER 或 AGENT',
    checked_by_id BIGINT UNSIGNED NOT NULL COMMENT '发起检查的用户或 Agent Run 标识',
    resource_versions_json JSON NOT NULL COMMENT '检查时 Release、Item、Pipeline、Test Run 与策略版本指纹',
    PRIMARY KEY (id),
    KEY idx_release_prechecks_release_time (release_id, checked_at),
    CONSTRAINT fk_release_prechecks_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_release_prechecks_release FOREIGN KEY (release_id) REFERENCES releases (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '由后端规则计算且写入后不可修改的发布前检查快照';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('release.read', 'release', 'read', '读取项目范围内的 Release Candidate 与 Precheck'),
    ('release.manage', 'release', 'manage', '创建或编辑 Release Candidate 与 Release Note'),
    ('release.precheck', 'release', 'precheck', '执行确定性发布前检查并保存不可变快照')
ON DUPLICATE KEY UPDATE
    resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'ADMIN') AND p.code LIKE 'release.%';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('PRODUCT', 'QA') AND p.code IN ('release.read', 'release.manage', 'release.precheck');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'DEVELOPER' AND p.code = 'release.read';
