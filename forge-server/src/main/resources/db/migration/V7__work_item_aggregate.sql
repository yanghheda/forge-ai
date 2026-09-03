CREATE TABLE project_item_sequences (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目编号序列的稳定业务标识',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '独占该编号序列的项目；每个项目只能存在一条记录',
    next_value BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '下一次成功分配时使用的正整数；允许因事务或删除产生缺口但禁止回退',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '序列事实的版本；本轮通过行锁串行分配而不作为普通编辑版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_project_item_sequences_project (project_id),
    CONSTRAINT fk_project_item_sequences_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '每个项目独立的 Work Item 原子编号序列；编号一经分配永不复用';

INSERT INTO project_item_sequences (project_id, next_value, version)
SELECT id, 1, 0 FROM projects;

CREATE TABLE work_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作项聚合的稳定业务标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区，用于所有业务查询的租户隔离且不可由客户端单独信任',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目，用于授权范围、编号分配和分页查询',
    item_number BIGINT UNSIGNED NOT NULL COMMENT '项目内单调分配的正整数编号；逻辑删除后仍不可复用',
    item_key VARCHAR(64) NOT NULL COMMENT '服务端使用项目短键与编号生成的展示标识，例如 FORGE-1',
    type VARCHAR(32) NOT NULL COMMENT '工作项业务类型；本轮开放 REQUIREMENT、UX_TASK、DEV_TASK 和 QA_TASK',
    title VARCHAR(255) NOT NULL COMMENT '工作项的简短可检索标题',
    description LONGTEXT NOT NULL COMMENT '工作项的详细说明；空字符串表示尚未补充',
    status VARCHAR(40) NOT NULL COMMENT '由工作项类型确定的生命周期状态；本轮仅写入创建初值且不开放迁移',
    priority VARCHAR(16) NOT NULL COMMENT '业务处理优先级；合法值由服务端 WorkItemPriority 定义',
    parent_id BIGINT UNSIGNED NULL COMMENT '树状从属工作项标识；为空表示没有父项，本轮不开放关系写入',
    assignee_user_id BIGINT UNSIGNED NULL COMMENT '当前负责人用户标识；为空表示尚未分配',
    reporter_user_id BIGINT UNSIGNED NOT NULL COMMENT '创建工作项的会话用户标识，用于责任追溯而非单独授权',
    due_at DATETIME(6) NULL COMMENT '期望完成的 UTC 时间；为空表示没有截止日期',
    severity VARCHAR(16) NULL COMMENT '缺陷严重级别；非 BUG 工作项为空且本轮不开放写入',
    blocked_at DATETIME(6) NULL COMMENT '进入阻塞状态的 UTC 时间；为空表示当前未阻塞，本轮不开放写入',
    blocked_reason VARCHAR(1000) NULL COMMENT '阻塞原因；未阻塞时为空，本轮不开放写入',
    blocked_by BIGINT UNSIGNED NULL COMMENT '最近执行阻塞操作的用户标识；未阻塞时为空，本轮不开放写入',
    created_at DATETIME(6) NOT NULL COMMENT '工作项创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '工作项最近一次基础字段修改的 UTC 时间',
    deleted_at DATETIME(6) NULL COMMENT '逻辑删除的 UTC 时间；为空表示对普通查询可见，删除不释放编号',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '工作项乐观锁版本；PATCH 的 expectedVersion 必须与其匹配',
    PRIMARY KEY (id),
    UNIQUE KEY uq_work_items_project_number (project_id, item_number),
    UNIQUE KEY uq_work_items_project_key (project_id, item_key),
    KEY idx_work_items_project_type_status (project_id, type, status),
    KEY idx_work_items_workspace_assignee_status (workspace_id, assignee_user_id, status),
    KEY idx_work_items_parent (parent_id),
    CONSTRAINT fk_work_items_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_work_items_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_work_items_parent FOREIGN KEY (parent_id) REFERENCES work_items (id),
    CONSTRAINT fk_work_items_assignee FOREIGN KEY (assignee_user_id) REFERENCES users (id),
    CONSTRAINT fk_work_items_reporter FOREIGN KEY (reporter_user_id) REFERENCES users (id),
    CONSTRAINT fk_work_items_blocked_by FOREIGN KEY (blocked_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '统一承载 Requirement 与角色 Task 的业务聚合；状态流转和关系留待后续会话';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('requirement.read', 'requirement', 'read', '读取项目范围内的 Requirement'),
    ('requirement.create', 'requirement', 'create', '在项目范围内创建 Requirement'),
    ('requirement.edit', 'requirement', 'edit', '修改 Requirement 的基础字段'),
    ('ux.read', 'ux', 'read', '读取项目范围内的 UX Task'),
    ('ux.create', 'ux', 'create', '在项目范围内创建 UX Task'),
    ('ux.edit', 'ux', 'edit', '修改 UX Task 的基础字段'),
    ('task.read', 'task', 'read', '读取项目范围内的 Dev 或 QA Task'),
    ('task.create', 'task', 'create', '在项目范围内创建 Dev 或 QA Task'),
    ('task.edit', 'task', 'edit', '修改 Dev 或 QA Task 的基础字段');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'ADMIN') AND p.code IN (
    'requirement.read', 'requirement.create', 'requirement.edit',
    'ux.read', 'ux.create', 'ux.edit', 'task.read', 'task.create', 'task.edit');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'PRODUCT' AND p.code IN (
    'requirement.read', 'requirement.create', 'requirement.edit', 'ux.read',
    'task.read', 'task.create', 'task.edit');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'UX' AND p.code IN (
    'requirement.read', 'ux.read', 'ux.create', 'ux.edit', 'task.read', 'task.create', 'task.edit');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code IN ('DEVELOPER', 'QA') AND p.code IN (
    'requirement.read', 'ux.read', 'task.read', 'task.create', 'task.edit');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'RELEASE_APPROVER' AND p.code IN ('requirement.read', 'ux.read', 'task.read');
