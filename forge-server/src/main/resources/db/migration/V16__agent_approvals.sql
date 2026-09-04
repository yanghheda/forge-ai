ALTER TABLE agent_tool_calls
    MODIFY COLUMN status VARCHAR(32) NOT NULL
    COMMENT 'Tool Call 状态：WAITING_APPROVAL、SUCCEEDED、REJECTED、EXPIRED 或 CANCELLED',
    MODIFY COLUMN result_json JSON NULL
    COMMENT '成功执行后的结构化结果；审批等待或拒绝时为空',
    MODIFY COLUMN finished_at DATETIME(6) NULL
    COMMENT 'Tool Call 完成时间；等待审批时为空',
    ADD COLUMN argument_hash CHAR(64) NULL
    COMMENT '规范化参数的 SHA-256 摘要，用于审批恢复时检测 TOCTOU'
    AFTER risk_level,
    ADD COLUMN approval_id CHAR(26) NULL
    COMMENT '需要人工审批时关联的审批标识；自动执行时为空'
    AFTER idempotency_key;

CREATE TABLE approvals (
    id CHAR(26) NOT NULL COMMENT '服务端生成的审批 ULID 标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '审批所属工作区，用于租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '审批所属项目，用于授权范围',
    run_id CHAR(26) NOT NULL COMMENT '触发审批的 Agent Run 标识',
    tool_call_id VARCHAR(64) NOT NULL COMMENT '被冻结的原始 Tool Call 标识',
    type VARCHAR(32) NOT NULL COMMENT '审批类型；本轮固定为 TOOL_EXECUTION',
    risk_level VARCHAR(8) NOT NULL COMMENT 'Tool 契约声明的风险等级',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING、APPROVED、REJECTED、EXPIRED 或 CANCELLED',
    requested_by BIGINT UNSIGNED NOT NULL COMMENT '请求执行 Tool 的 Run 发起用户',
    approver_user_id BIGINT UNSIGNED NULL COMMENT '作出决定的用户；未决定时为空',
    tool_name VARCHAR(64) NOT NULL COMMENT '冻结的 Tool 名称',
    tool_version INT UNSIGNED NOT NULL COMMENT '冻结的 Tool Contract 版本',
    argument_hash CHAR(64) NOT NULL COMMENT '规范化参数 SHA-256 摘要',
    frozen_arguments_encrypted TEXT NOT NULL COMMENT '使用服务端密钥 AES-GCM 加密的完整规范化参数',
    resource_versions_json JSON NOT NULL COMMENT '审批时读取的权威资源类型、标识与版本快照',
    reason VARCHAR(500) NOT NULL COMMENT '向审批人解释风险与影响的脱敏原因',
    expires_at DATETIME(6) NOT NULL COMMENT '审批最晚可决策时间；过期后必须重新规划',
    decided_at DATETIME(6) NULL COMMENT '批准、拒绝、过期或取消的时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '决策接口使用的乐观锁版本',
    created_at DATETIME(6) NOT NULL COMMENT '审批创建 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_approvals_run_tool_call (run_id, tool_call_id),
    KEY idx_approvals_project_status_expiry (project_id, status, expires_at),
    CONSTRAINT fk_approvals_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_approvals_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_approvals_run FOREIGN KEY (run_id) REFERENCES agent_runs (id),
    CONSTRAINT fk_approvals_requester FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_approvals_approver FOREIGN KEY (approver_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent Tool 人工审批的冻结事实；浏览器会话不是审批事实来源';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('approval.decide', 'approval', 'decide', '批准或拒绝项目范围内他人发起的 Agent Tool 审批')
ON DUPLICATE KEY UPDATE resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'approval.decide'
WHERE r.code IN ('OWNER', 'ADMIN');
