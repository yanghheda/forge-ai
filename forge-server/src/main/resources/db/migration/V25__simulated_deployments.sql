ALTER TABLE releases
    MODIFY COLUMN status VARCHAR(24) NOT NULL
    COMMENT '发布状态：DRAFT、PRECHECKED、READY_FOR_APPROVAL、APPROVED、DEPLOYING、RELEASED 或 FAILED';

CREATE TABLE deployments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '部署记录递增标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '部署所属工作区，用于租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '部署所属项目，用于项目授权',
    release_id BIGINT UNSIGNED NOT NULL COMMENT '被部署的 Release Candidate 标识',
    mode VARCHAR(16) NOT NULL COMMENT '部署模式；MVP 仅允许 SIMULATED',
    status VARCHAR(32) NOT NULL COMMENT '部署状态：PENDING_APPROVAL、APPROVED、DEPLOYING、SUCCEEDED、FAILED、REJECTED 或 EXPIRED',
    requested_by BIGINT UNSIGNED NOT NULL COMMENT '发起部署请求的用户标识',
    approver_user_id BIGINT UNSIGNED NULL COMMENT '审批用户标识；尚未决策时为空',
    approval_expires_at DATETIME(6) NOT NULL COMMENT '人工审批失效的 UTC 时间',
    decided_at DATETIME(6) NULL COMMENT '审批决定或过期的 UTC 时间；未决策时为空',
    release_version BIGINT UNSIGNED NOT NULL COMMENT '申请时冻结的 Release 乐观锁版本',
    precheck_id BIGINT UNSIGNED NOT NULL COMMENT '申请时冻结的 PASS Precheck 快照标识',
    argument_hash CHAR(64) NOT NULL COMMENT '规范化部署参数的 SHA-256 摘要',
    simulate_failure BOOLEAN NOT NULL COMMENT '测试专用确定性失败开关；不代表真实环境结果',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '调用方提供的部署请求幂等键',
    result_code VARCHAR(64) NULL COMMENT '模拟器稳定结果码；执行前为空',
    result_summary VARCHAR(500) NULL COMMENT '明确声明模拟性质的结果摘要；执行前为空',
    approval_source VARCHAR(16) NOT NULL COMMENT '审批来源：HUMAN_REQUEST 或 AGENT_TOOL',
    agent_approval_id CHAR(26) NULL COMMENT 'Agent HIGH Tool 已批准事实标识；人工入口为空',
    created_at DATETIME(6) NOT NULL COMMENT '部署请求创建 UTC 时间',
    started_at DATETIME(6) NULL COMMENT 'worker 开始模拟执行的 UTC 时间；未执行时为空',
    finished_at DATETIME(6) NULL COMMENT '模拟执行结束的 UTC 时间；未结束时为空',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '审批与 worker 抢占使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_deployments_scope_idempotency (workspace_id, project_id, idempotency_key),
    KEY idx_deployments_status_created (status, created_at),
    KEY idx_deployments_release (workspace_id, project_id, release_id),
    CONSTRAINT fk_deployments_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_deployments_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_deployments_release FOREIGN KEY (release_id) REFERENCES releases (id),
    CONSTRAINT fk_deployments_requester FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_deployments_approver FOREIGN KEY (approver_user_id) REFERENCES users (id),
    CONSTRAINT fk_deployments_precheck FOREIGN KEY (precheck_id) REFERENCES release_prechecks (id),
    CONSTRAINT fk_deployments_agent_approval FOREIGN KEY (agent_approval_id) REFERENCES approvals (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'HIGH 审批保护且明确标记为模拟执行的部署记录';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('release.deploy', 'release', 'deploy', '申请或执行经过 HIGH 审批的模拟部署')
ON DUPLICATE KEY UPDATE
    resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'release.deploy'
WHERE r.code IN ('OWNER', 'ADMIN', 'PRODUCT');
