CREATE TABLE secrets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '加密凭据的内部标识，不作为普通资源 API 暴露',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '凭据所属工作区，用于租户隔离与加密附加认证数据',
    type VARCHAR(64) NOT NULL COMMENT '凭据用途类型，例如 GITLAB_TOKEN',
    ciphertext TEXT NOT NULL COMMENT 'AES-256-GCM 密文与认证标签的 Base64 表示',
    iv VARCHAR(64) NOT NULL COMMENT '本次加密唯一的 96 位随机 IV 的 Base64 表示',
    key_version INT UNSIGNED NOT NULL COMMENT '解密所需部署主密钥版本',
    fingerprint VARCHAR(32) NOT NULL COMMENT '用于管理员识别轮换结果的不可逆 HMAC 短指纹',
    created_at DATETIME(6) NOT NULL COMMENT '凭据首次创建的 UTC 时间',
    rotated_at DATETIME(6) NULL COMMENT '该密文由轮换操作写入的 UTC 时间，首次保存为空',
    PRIMARY KEY (id),
    KEY idx_secrets_workspace_type (workspace_id, type),
    CONSTRAINT fk_secrets_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '仅供受控服务临时解密的工作区外部凭据密文';

CREATE TABLE gitlab_connections (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'GitLab 连接的本地稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '连接所属工作区，所有读写必须显式携带该范围',
    name VARCHAR(120) NOT NULL COMMENT '工作区内供管理员辨识连接的名称',
    base_url VARCHAR(500) NOT NULL COMMENT '已规范化并通过 SSRF 策略校验的 GitLab HTTPS 根地址',
    credential_secret_id BIGINT UNSIGNED NOT NULL COMMENT '当前 GitLab Token 对应的加密凭据标识',
    webhook_secret_id BIGINT UNSIGNED NULL COMMENT '后续 Webhook 会话使用的加密 Secret，本轮保持为空',
    status VARCHAR(32) NOT NULL COMMENT '连接状态：UNVERIFIED、ACTIVE 或 ERROR',
    last_tested_at DATETIME(6) NULL COMMENT '最近一次远端连接测试完成的 UTC 时间',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建该连接的工作区管理员用户标识',
    created_at DATETIME(6) NOT NULL COMMENT '连接创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '连接配置或测试状态最近更新的 UTC 时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '配置更新与 Token 轮换使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_gitlab_connections_workspace_name (workspace_id, name),
    CONSTRAINT fk_gitlab_connections_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_gitlab_connections_secret FOREIGN KEY (credential_secret_id) REFERENCES secrets (id),
    CONSTRAINT fk_gitlab_connections_webhook_secret FOREIGN KEY (webhook_secret_id) REFERENCES secrets (id),
    CONSTRAINT fk_gitlab_connections_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '工作区管理员配置的 GitLab API 连接，不包含明文 Token';

CREATE TABLE git_repositories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '绑定仓库的本地稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '仓库所属工作区，必须与连接和项目范围一致',
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'ForgeAI 项目标识，MVP 同时仅绑定一个 ACTIVE 仓库',
    connection_id BIGINT UNSIGNED NOT NULL COMMENT '读取该远端仓库所使用的 GitLab 连接标识',
    remote_project_id VARCHAR(128) NOT NULL COMMENT 'GitLab 返回的不可变项目标识',
    path_with_namespace VARCHAR(500) NOT NULL COMMENT 'GitLab 仓库的命名空间完整路径',
    http_url VARCHAR(1000) NOT NULL COMMENT 'GitLab 返回的仓库浏览或克隆 HTTPS 地址，不含凭据',
    default_branch VARCHAR(255) NULL COMMENT '远端默认分支；GitLab 未设置时为空',
    status VARCHAR(32) NOT NULL COMMENT '绑定状态，MVP 使用 ACTIVE',
    last_synced_at DATETIME(6) NOT NULL COMMENT '最近一次从 GitLab 读取仓库事实的 UTC 时间',
    created_at DATETIME(6) NOT NULL COMMENT '仓库绑定创建的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '仓库远端快照最近更新的 UTC 时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '仓库绑定更新使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_git_repositories_connection_remote (connection_id, remote_project_id),
    UNIQUE KEY uq_git_repositories_project_active (project_id, status),
    KEY idx_git_repositories_workspace_project (workspace_id, project_id),
    CONSTRAINT fk_git_repositories_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_git_repositories_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_git_repositories_connection FOREIGN KEY (connection_id) REFERENCES gitlab_connections (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'ForgeAI 项目与 GitLab 仓库的租户范围绑定及只读远端快照';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('integration.manage', 'integration', 'manage', '配置、轮换和测试工作区外部系统连接'),
    ('repo.read', 'repo', 'read', '读取并绑定项目范围内的源代码仓库')
ON DUPLICATE KEY UPDATE resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'ADMIN') AND p.code IN ('integration.manage', 'repo.read');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'repo.read'
WHERE r.code = 'DEVELOPER';
