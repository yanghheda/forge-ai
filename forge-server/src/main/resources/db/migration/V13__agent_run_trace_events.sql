CREATE TABLE agent_runs (
    id CHAR(26) NOT NULL COMMENT '服务端生成的 ULID Run 标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT 'Run 所属工作区，用于强制租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'Run 所属项目，用于项目授权与查询范围',
    work_item_id BIGINT UNSIGNED NULL COMMENT '可选上下文工作项；空值表示仅使用项目范围',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '发起 Run 的用户标识',
    skill VARCHAR(32) NOT NULL COMMENT '本轮允许的 Product 或 UX Skill',
    message_redacted VARCHAR(500) NOT NULL COMMENT '不含原始正文的输入长度摘要',
    client_request_id VARCHAR(100) NOT NULL COMMENT '客户端重试使用的幂等请求标识',
    request_hash CHAR(64) NOT NULL COMMENT '识别幂等键是否被不同请求参数重用的 SHA-256 指纹',
    status VARCHAR(32) NOT NULL COMMENT 'Backend 权威 Run 状态',
    model_provider VARCHAR(64) NULL COMMENT '实际模型供应方；Fake Runner 阶段为空',
    model_name VARCHAR(128) NULL COMMENT '实际模型名称；Fake Runner 阶段为空',
    prompt_version VARCHAR(64) NOT NULL COMMENT '执行所使用的 Prompt 或 Fake Runner 版本',
    started_at DATETIME(6) NULL COMMENT '开始执行时间；排队期间为空',
    finished_at DATETIME(6) NULL COMMENT '进入终态时间；未结束时为空',
    token_input BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '输入 Token 计量；Fake Runner 固定为零',
    token_output BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '输出 Token 计量；Fake Runner 固定为零',
    cost DECIMAL(18, 8) NOT NULL DEFAULT 0 COMMENT '本次运行成本；Fake Runner 固定为零',
    error_code VARCHAR(100) NULL COMMENT '稳定失败码；成功或未结束时为空',
    last_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已提交的最后一个持久业务事件序号',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Run 乐观演进版本',
    created_at DATETIME(6) NOT NULL COMMENT 'Run 创建时间',
    updated_at DATETIME(6) NOT NULL COMMENT 'Run 最近状态更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_agent_runs_client_request (workspace_id, project_id, user_id, client_request_id),
    KEY idx_agent_runs_project_created (project_id, created_at),
    KEY idx_agent_runs_user_status (user_id, status),
    CONSTRAINT fk_agent_runs_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_agent_runs_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_agent_runs_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_agent_runs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent Run 对外状态与事件序号的 Backend 权威事实';

CREATE TABLE agent_steps (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Run 步骤数据库标识',
    run_id CHAR(26) NOT NULL COMMENT '步骤所属 Agent Run 标识',
    step_no INT UNSIGNED NOT NULL COMMENT 'Run 内从一开始的稳定步骤序号',
    type VARCHAR(32) NOT NULL COMMENT '步骤执行类型；本轮固定为 FAKE',
    name VARCHAR(200) NOT NULL COMMENT '供时间线显示的简短步骤名称',
    status VARCHAR(32) NOT NULL COMMENT '步骤当前执行状态',
    input_summary VARCHAR(500) NOT NULL COMMENT '脱敏后的步骤输入摘要',
    output_summary VARCHAR(500) NULL COMMENT '脱敏后的步骤输出摘要；未完成时为空',
    error_code VARCHAR(100) NULL COMMENT '稳定步骤失败码；成功或未结束时为空',
    started_at DATETIME(6) NOT NULL COMMENT '步骤开始执行时间',
    finished_at DATETIME(6) NULL COMMENT '步骤进入终态时间；执行中为空',
    PRIMARY KEY (id),
    UNIQUE KEY uq_agent_steps_run_step (run_id, step_no),
    CONSTRAINT fk_agent_steps_run FOREIGN KEY (run_id) REFERENCES agent_runs (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent Run 可展示的脱敏步骤 Trace';

CREATE TABLE agent_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '持久事件数据库标识',
    run_id CHAR(26) NOT NULL COMMENT '事件所属 Agent Run 标识',
    sequence BIGINT UNSIGNED NOT NULL COMMENT 'Run 内严格递增且连续的业务事件序号',
    event_type VARCHAR(64) NOT NULL COMMENT 'SSE event 字段使用的稳定事件类型',
    request_id VARCHAR(100) NOT NULL COMMENT '触发该事件的请求关联标识',
    payload_json JSON NOT NULL COMMENT '仅包含 UI 恢复所需结构化字段的事件载荷',
    created_at DATETIME(6) NOT NULL COMMENT '事件与序号提交到 MySQL 的时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_agent_events_run_sequence (run_id, sequence),
    CONSTRAINT fk_agent_events_run FOREIGN KEY (run_id) REFERENCES agent_runs (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'SSE 重放使用的追加写持久业务事件';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('agent.run', 'agent', 'run', '在授权项目范围内创建并读取 Agent Run')
ON DUPLICATE KEY UPDATE resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'agent.run'
WHERE r.code IN ('OWNER', 'ADMIN', 'PRODUCT', 'UX');
