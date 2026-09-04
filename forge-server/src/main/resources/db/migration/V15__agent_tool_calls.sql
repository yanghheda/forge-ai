ALTER TABLE agent_runs
    ADD COLUMN medium_tool_confirmation VARCHAR(8) NOT NULL DEFAULT 'ASK'
    COMMENT '本轮 MEDIUM 风险 Tool 的确认策略：ASK 需确认后执行、ALLOW 直接执行、DENY 拒绝执行'
    AFTER skill;

CREATE TABLE agent_tool_calls (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Tool Call 数据库标识',
    run_id CHAR(26) NOT NULL COMMENT 'Tool Call 所属 Agent Run 标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT 'Run 所属工作区，用于强制租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'Run 所属项目，用于项目授权与查询范围',
    tool_call_id VARCHAR(64) NOT NULL COMMENT 'Agent 侧生成的本次调用标识，与 Run 组成幂等键',
    tool_name VARCHAR(64) NOT NULL COMMENT '被执行的 Tool 契约名称',
    tool_version INT UNSIGNED NOT NULL COMMENT '被执行的 Tool 契约版本',
    risk_level VARCHAR(8) NOT NULL COMMENT '契约声明的风险等级；本轮只持久化实际执行的 MEDIUM 写操作',
    status VARCHAR(32) NOT NULL COMMENT 'Tool Call 当前状态；当前固定为 SUCCEEDED，失败调用不占幂等键',
    arguments_json JSON NOT NULL COMMENT '通过契约 Schema 校验的结构化输入参数',
    result_json JSON NOT NULL COMMENT '应用服务返回并投影后的结构化结果',
    idempotency_key VARCHAR(128) NOT NULL COMMENT 'runId 与 toolCallId 拼接的幂等键，成功副作用只发生一次',
    created_at DATETIME(6) NOT NULL COMMENT 'Tool Call 提交时间',
    finished_at DATETIME(6) NOT NULL COMMENT 'Tool Call 完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_agent_tool_calls_run_call (run_id, tool_call_id),
    UNIQUE KEY uq_agent_tool_calls_idempotency (idempotency_key),
    KEY idx_agent_tool_calls_project_created (project_id, created_at),
    CONSTRAINT fk_agent_tool_calls_run FOREIGN KEY (run_id) REFERENCES agent_runs (id),
    CONSTRAINT fk_agent_tool_calls_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_agent_tool_calls_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent Tool Call 的幂等事实与结构化结果；Backend 是 Tool 副作用的唯一权威';
