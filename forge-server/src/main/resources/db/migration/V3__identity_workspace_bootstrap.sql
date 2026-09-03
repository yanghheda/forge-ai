-- 会话 06 的初始化模型；只建立首个 Owner 所需身份、租户、角色与审计事实。
ALTER TABLE instance_settings
    MODIFY COLUMN default_organization_id BIGINT UNSIGNED NULL
        COMMENT '初始化后创建的默认组织标识；为空表示实例尚未完成初始化';

CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '实例内用户的稳定业务标识',
    email VARCHAR(320) NOT NULL COMMENT '用户输入并用于展示的电子邮箱地址',
    normalized_email VARCHAR(320) NOT NULL COMMENT '去除首尾空白并小写后的登录唯一键',
    display_name VARCHAR(120) NOT NULL COMMENT '界面展示的用户名称，不参与身份唯一性判断',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要，只允许服务端验证且禁止回显或记录日志',
    status VARCHAR(32) NOT NULL COMMENT '用户生命周期状态；初始化用户固定为 ACTIVE',
    failed_login_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连续登录失败次数，由后续登录会话更新',
    locked_until DATETIME(6) NULL COMMENT '账户锁定截止 UTC 时间；为空表示未按时间锁定',
    last_login_at DATETIME(6) NULL COMMENT '最近一次成功登录的 UTC 时间；初始化时为空',
    created_at DATETIME(6) NOT NULL COMMENT '用户创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '用户最近一次更新的 UTC 时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户并发更新使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_normalized_email (normalized_email),
    KEY idx_users_status (status)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '实例用户及其本地密码认证事实';

CREATE TABLE organizations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '组织的稳定业务标识',
    name VARCHAR(120) NOT NULL COMMENT '组织的界面展示名称',
    slug VARCHAR(80) NOT NULL COMMENT '组织在实例内唯一且可用于路由的规范化短名',
    owner_user_id BIGINT UNSIGNED NOT NULL COMMENT '组织所有者用户标识；初始化时指向首个用户',
    created_at DATETIME(6) NOT NULL COMMENT '组织创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '组织最近一次更新的 UTC 时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '组织并发更新使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_organizations_slug (slug),
    CONSTRAINT fk_organizations_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '实例内承载 Workspace 的组织边界';

CREATE TABLE workspaces (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作区的稳定业务标识',
    organization_id BIGINT UNSIGNED NOT NULL COMMENT '所属组织标识，用于限定工作区命名与管理范围',
    name VARCHAR(120) NOT NULL COMMENT '工作区的界面展示名称',
    slug VARCHAR(80) NOT NULL COMMENT '组织内唯一且可用于路由的规范化短名',
    status VARCHAR(32) NOT NULL COMMENT '工作区生命周期状态；初始化工作区固定为 ACTIVE',
    settings_json JSON NOT NULL COMMENT '尚未拆分为独立字段的工作区扩展设置',
    created_at DATETIME(6) NOT NULL COMMENT '工作区创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '工作区最近一次更新的 UTC 时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '工作区并发更新使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_workspaces_organization_slug (organization_id, slug),
    CONSTRAINT fk_workspaces_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '组织内的数据与权限租户边界';

CREATE TABLE workspace_members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作区成员关系的稳定业务标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区标识，是成员访问范围的租户边界',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '加入工作区的实例用户标识',
    status VARCHAR(32) NOT NULL COMMENT '成员关系状态；初始化成员固定为 ACTIVE',
    joined_at DATETIME(6) NOT NULL COMMENT '成员正式加入工作区的 UTC 时间',
    created_at DATETIME(6) NOT NULL COMMENT '成员关系创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '成员关系最近一次更新的 UTC 时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成员关系并发更新使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_workspace_members_workspace_user (workspace_id, user_id),
    KEY idx_workspace_members_user_status (user_id, status),
    CONSTRAINT fk_workspace_members_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_workspace_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户加入 Workspace 后形成的租户访问关系';

CREATE TABLE roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色的稳定业务标识',
    workspace_id BIGINT UNSIGNED NULL COMMENT '自定义角色所属工作区；系统角色为空',
    workspace_scope_key BIGINT UNSIGNED AS (IFNULL(workspace_id, 0)) STORED COMMENT '将系统角色空范围规范为 0，以便唯一约束覆盖 NULL',
    code VARCHAR(64) NOT NULL COMMENT '角色稳定代码；初始化仅创建系统 OWNER',
    name VARCHAR(120) NOT NULL COMMENT '角色的界面展示名称',
    system_role BOOLEAN NOT NULL COMMENT '是否为实例预置且不能按普通工作区角色删除',
    description VARCHAR(500) NOT NULL COMMENT '角色业务能力与适用范围说明',
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_workspace_code (workspace_scope_key, code),
    KEY idx_roles_code_system (code, system_role),
    CONSTRAINT fk_roles_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '可分配给 Workspace 成员的角色定义';

CREATE TABLE member_roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成员角色分配的稳定业务标识',
    workspace_member_id BIGINT UNSIGNED NOT NULL COMMENT '获得角色的工作区成员关系标识',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '分配给成员的角色标识',
    project_id BIGINT UNSIGNED NULL COMMENT '项目级角色范围；为空表示作用于整个 Workspace',
    project_scope_key BIGINT UNSIGNED AS (IFNULL(project_id, 0)) STORED COMMENT '将 Workspace 级空项目范围规范为 0，以便唯一约束覆盖 NULL',
    created_at DATETIME(6) NOT NULL COMMENT '角色分配创建的 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_roles_scope (workspace_member_id, role_id, project_scope_key),
    CONSTRAINT fk_member_roles_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_member_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Workspace 成员在工作区或项目范围内的角色分配';

CREATE TABLE audit_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '只追加审计记录的递增标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '审计事件所属工作区，用于服务端租户隔离查询',
    project_id BIGINT UNSIGNED NULL COMMENT '审计事件所属项目；实例初始化事件为空',
    actor_type VARCHAR(32) NOT NULL COMMENT '操作者类型；初始化事件使用 USER',
    actor_id BIGINT UNSIGNED NOT NULL COMMENT '执行动作的用户标识',
    action VARCHAR(100) NOT NULL COMMENT '稳定的审计动作代码',
    resource_type VARCHAR(64) NOT NULL COMMENT '被操作资源的稳定类型代码',
    resource_id BIGINT UNSIGNED NOT NULL COMMENT '被操作资源的业务标识',
    result VARCHAR(32) NOT NULL COMMENT '审计动作结果；成功初始化记录 SUCCESS',
    request_id VARCHAR(80) NOT NULL COMMENT '贯穿 HTTP 响应与日志的请求标识',
    run_id VARCHAR(80) NULL COMMENT '关联 Agent Run 的外部标识；人工初始化时为空',
    metadata_redacted_json JSON NOT NULL COMMENT '经过字段白名单脱敏的审计补充信息，禁止密码与摘要',
    created_at DATETIME(6) NOT NULL COMMENT '审计事实创建的 UTC 时间',
    PRIMARY KEY (id),
    KEY idx_audit_logs_workspace_created (workspace_id, created_at),
    KEY idx_audit_logs_resource (resource_type, resource_id),
    CONSTRAINT fk_audit_logs_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '关键业务与安全动作的应用级只追加审计记录';

ALTER TABLE instance_settings
    ADD CONSTRAINT fk_instance_settings_default_organization
        FOREIGN KEY (default_organization_id) REFERENCES organizations (id);
