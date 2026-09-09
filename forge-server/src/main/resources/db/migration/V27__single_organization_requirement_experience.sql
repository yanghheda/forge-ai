ALTER TABLE instance_settings
    ADD COLUMN default_workspace_id BIGINT UNSIGNED NULL
        COMMENT '单组织产品模型内部使用的默认工作区标识，不向新客户端暴露' AFTER default_organization_id,
    ADD COLUMN default_project_id BIGINT UNSIGNED NULL
        COMMENT '单组织产品模型内部使用的默认项目标识，不向新客户端暴露' AFTER default_workspace_id;

UPDATE instance_settings settings
JOIN (
    SELECT workspaces.organization_id, MIN(workspaces.id) AS workspace_id
    FROM workspaces
    WHERE workspaces.status = 'ACTIVE'
    GROUP BY workspaces.organization_id
) defaults ON defaults.organization_id = settings.default_organization_id
SET settings.default_workspace_id = defaults.workspace_id
WHERE settings.id = 1;

INSERT INTO projects
    (workspace_id, `key`, name, description, status, created_by, created_at, updated_at, version)
SELECT workspaces.id, 'REQ', organizations.name, '单组织产品模型的内部默认需求范围', 'ACTIVE',
       organizations.owner_user_id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM instance_settings settings
JOIN workspaces ON workspaces.id = settings.default_workspace_id
JOIN organizations ON organizations.id = workspaces.organization_id
WHERE settings.id = 1
  AND NOT EXISTS (SELECT 1 FROM projects WHERE projects.workspace_id = workspaces.id);

INSERT INTO project_item_sequences (project_id, next_value, version)
SELECT projects.id, 1, 0
FROM projects
LEFT JOIN project_item_sequences sequences ON sequences.project_id = projects.id
WHERE sequences.id IS NULL;

UPDATE instance_settings settings
JOIN (
    SELECT projects.workspace_id, MIN(projects.id) AS project_id
    FROM projects
    WHERE projects.status = 'ACTIVE'
    GROUP BY projects.workspace_id
) defaults ON defaults.workspace_id = settings.default_workspace_id
SET settings.default_project_id = defaults.project_id
WHERE settings.id = 1;

ALTER TABLE instance_settings
    ADD CONSTRAINT fk_instance_settings_default_workspace
        FOREIGN KEY (default_workspace_id) REFERENCES workspaces (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_instance_settings_default_project
        FOREIGN KEY (default_project_id) REFERENCES projects (id) ON DELETE SET NULL;

CREATE TABLE requirement_participants (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '需求角色参与关系的稳定业务标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '内部兼容工作区范围，用于服务端防御性租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '内部兼容项目范围，用于服务端防御性资源隔离',
    requirement_id BIGINT UNSIGNED NOT NULL COMMENT '被关联人员参与交付的 Requirement 标识',
    role_code VARCHAR(32) NOT NULL COMMENT '人员在该需求承担的角色；仅允许 PRODUCT、UX、DEVELOPER、QA',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '承担该需求角色的有效组织成员用户标识',
    assigned_by BIGINT UNSIGNED NOT NULL COMMENT '最近设置该参与人的操作者用户标识',
    created_at DATETIME(6) NOT NULL COMMENT '参与关系首次创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '参与关系最近更新的 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_requirement_participants_role (requirement_id, role_code),
    KEY idx_requirement_participants_user (workspace_id, user_id, requirement_id),
    CONSTRAINT fk_requirement_participants_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_requirement_participants_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_requirement_participants_requirement FOREIGN KEY (requirement_id) REFERENCES work_items (id),
    CONSTRAINT fk_requirement_participants_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_requirement_participants_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT chk_requirement_participants_role
        CHECK (role_code IN ('PRODUCT', 'UX', 'DEVELOPER', 'QA'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Requirement 按产品、体验、开发和测试角色关联组织成员的事实';
