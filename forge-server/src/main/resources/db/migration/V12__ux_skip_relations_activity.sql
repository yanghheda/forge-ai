CREATE TABLE project_policies (
    project_id BIGINT UNSIGNED NOT NULL COMMENT '策略所属项目；每个项目恰好一条策略事实',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；策略查询必须同时匹配租户范围',
    allow_skip_ux BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许符合分类条件的 Requirement 跳过 UX；默认关闭',
    updated_by BIGINT UNSIGNED NOT NULL COMMENT '最近修改策略的用户标识；初始化时为项目创建者',
    updated_at DATETIME(6) NOT NULL COMMENT '策略最近修改的 UTC 时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '策略乐观锁版本；修改时必须精确匹配',
    PRIMARY KEY (project_id),
    KEY idx_project_policies_workspace (workspace_id, project_id),
    CONSTRAINT fk_project_policies_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_policies_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_project_policies_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目级确定性工作流策略；权限允许后仍须通过这里的业务约束';

INSERT INTO project_policies (project_id, workspace_id, allow_skip_ux, updated_by, updated_at, version)
SELECT id, workspace_id, FALSE, created_by, UTC_TIMESTAMP(6), 0 FROM projects;

CREATE TABLE work_item_labels (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作项标签关系的稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；读取标签时必须匹配租户范围',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目；读取标签时必须匹配项目范围',
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT '被分类的工作项标识',
    label VARCHAR(40) NOT NULL COMMENT '服务端识别的稳定分类；跳过 UX 仅接受 BACKEND_ONLY、OPS 或 INTERNAL_TECH',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '添加分类的用户标识，用于追溯',
    created_at DATETIME(6) NOT NULL COMMENT '分类添加的 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_work_item_labels_item_label (work_item_id, label),
    KEY idx_work_item_labels_scope (workspace_id, project_id, work_item_id),
    CONSTRAINT fk_work_item_labels_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_work_item_labels_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_work_item_labels_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_work_item_labels_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '工作项的显式业务分类；用于可解释的确定性策略判断';

CREATE TABLE work_item_relations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作项非树状关系的稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '关系所属工作区；两端必须同属该租户',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '关系所属项目；MVP 两端必须同属该项目',
    source_id BIGINT UNSIGNED NOT NULL COMMENT '关系起点工作项标识',
    target_id BIGINT UNSIGNED NOT NULL COMMENT '关系终点工作项标识；不得等于起点',
    relation_type VARCHAR(32) NOT NULL COMMENT '关系语义；支持 DEPENDS_ON、BLOCKS 和 RELATES_TO',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建关系的用户标识',
    created_at DATETIME(6) NOT NULL COMMENT '关系创建的 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_work_item_relations_direction (source_id, target_id, relation_type),
    KEY idx_work_item_relations_scope_source (workspace_id, project_id, source_id),
    KEY idx_work_item_relations_scope_target (workspace_id, project_id, target_id),
    CONSTRAINT chk_work_item_relations_not_self CHECK (source_id <> target_id),
    CONSTRAINT fk_work_item_relations_source FOREIGN KEY (source_id) REFERENCES work_items (id),
    CONSTRAINT fk_work_item_relations_target FOREIGN KEY (target_id) REFERENCES work_items (id),
    CONSTRAINT fk_work_item_relations_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_work_item_relations_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_work_item_relations_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Work Item 间有向非树状关系；树状从属仍只使用 work_items.parent_id';

CREATE TABLE comments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论的稳定业务标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；评论查询必须匹配租户范围',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目；评论查询必须匹配项目范围',
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT '评论归属的工作项标识',
    author_user_id BIGINT UNSIGNED NOT NULL COMMENT '评论作者用户标识',
    body VARCHAR(4000) NOT NULL COMMENT '评论正文；去除首尾空白后不能为空',
    created_at DATETIME(6) NOT NULL COMMENT '评论创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '评论最近编辑的 UTC 时间；本轮仅创建因此等于创建时间',
    deleted_at DATETIME(6) NULL COMMENT '逻辑删除时间；为空表示可在 Activity 中展示',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论乐观锁版本；本轮创建初值为零',
    PRIMARY KEY (id),
    KEY idx_comments_timeline (work_item_id, created_at, id),
    KEY idx_comments_scope (workspace_id, project_id, work_item_id),
    CONSTRAINT fk_comments_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_comments_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_comments_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_comments_author FOREIGN KEY (author_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Work Item 用户讨论事实；统一 Activity 只投影未删除评论';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('ux.skip', 'ux', 'skip', '在项目策略与分类允许时跳过 Requirement UX 阶段')
ON DUPLICATE KEY UPDATE resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'ux.skip'
WHERE r.code IN ('OWNER', 'ADMIN', 'PRODUCT');
