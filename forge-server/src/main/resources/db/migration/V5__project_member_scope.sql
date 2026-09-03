CREATE TABLE projects (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目的稳定业务标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区，用于服务端租户隔离查询且不可由客户端单独信任',
    `key` VARCHAR(32) NOT NULL COMMENT '工作区内唯一的项目短键，用于路由和后续业务编号前缀',
    name VARCHAR(160) NOT NULL COMMENT '项目的界面展示名称',
    description VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '项目的可选业务说明；为空表示尚未补充描述',
    status VARCHAR(32) NOT NULL COMMENT '项目生命周期状态；本轮仅允许 ACTIVE 或 ARCHIVED',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建项目的用户标识，用于审计与追溯，不作为授权唯一依据',
    created_at DATETIME(6) NOT NULL COMMENT '项目创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '项目最近一次修改的 UTC 时间',
    archived_at DATETIME(6) NULL COMMENT '项目归档的 UTC 时间；为空表示项目尚未归档',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '项目乐观锁版本；归档请求必须匹配 expectedVersion',
    PRIMARY KEY (id),
    UNIQUE KEY uq_projects_workspace_key (workspace_id, `key`),
    KEY idx_projects_workspace_status (workspace_id, status),
    CONSTRAINT fk_projects_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '工作区内独立研发项目的业务事实；归档后仍保留全部关联记录';

CREATE TABLE project_members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目成员范围关系的稳定业务标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区的冗余租户范围，用于防御性隔离查询',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '被授予访问范围的项目标识',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '获得项目访问范围的用户标识；必须已经是该工作区成员',
    status VARCHAR(32) NOT NULL COMMENT '项目成员关系状态；ACTIVE 可访问，REMOVED 立即失效但保留关系历史',
    created_at DATETIME(6) NOT NULL COMMENT '项目成员关系首次创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '项目成员关系最近一次状态变更的 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_project_members_project_user (project_id, user_id),
    KEY idx_project_members_workspace_user_status (workspace_id, user_id, status),
    CONSTRAINT fk_project_members_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_project_members_workspace_user FOREIGN KEY (workspace_id, user_id)
        REFERENCES workspace_members (workspace_id, user_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户在项目内的访问范围；不承担会话 10 的项目角色与权限定义';
