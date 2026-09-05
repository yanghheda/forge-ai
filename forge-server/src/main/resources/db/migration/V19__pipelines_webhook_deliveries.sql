CREATE TABLE pipeline_runs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '本地 Pipeline 快照稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT 'Pipeline 所属工作区租户范围',
    repository_id BIGINT UNSIGNED NOT NULL COMMENT 'Pipeline 所属 Git 仓库绑定标识',
    merge_request_id BIGINT UNSIGNED NULL COMMENT '可选关联 MR；分支 Pipeline 或尚未匹配时为空',
    remote_pipeline_id BIGINT UNSIGNED NOT NULL COMMENT 'GitLab 在项目内分配的 Pipeline 标识',
    ref VARCHAR(255) NOT NULL COMMENT 'Pipeline 执行所针对的分支或标签',
    commit_sha VARCHAR(64) NOT NULL COMMENT 'Pipeline 执行所针对的提交 SHA',
    status VARCHAR(32) NOT NULL COMMENT 'GitLab 返回的 Pipeline 执行状态',
    web_url VARCHAR(1000) NOT NULL COMMENT '不含凭据的 GitLab Pipeline 页面地址',
    started_at DATETIME(6) NULL COMMENT 'GitLab 记录的开始时间；尚未开始时为空',
    finished_at DATETIME(6) NULL COMMENT 'GitLab 记录的结束时间；未结束时为空',
    remote_updated_at DATETIME(6) NOT NULL COMMENT '用于拒绝乱序覆盖的 GitLab 对象更新时间',
    last_synced_at DATETIME(6) NOT NULL COMMENT 'ForgeAI 最近同步该快照的 UTC 时间',
    summary_json JSON NOT NULL COMMENT 'UI 展示所需的有界标准化摘要，不含完整 Job 日志',
    PRIMARY KEY (id),
    UNIQUE KEY uq_pipeline_runs_repository_remote (repository_id, remote_pipeline_id),
    KEY idx_pipeline_runs_workspace_repository (workspace_id, repository_id, id),
    CONSTRAINT fk_pipeline_runs_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_pipeline_runs_repository FOREIGN KEY (repository_id) REFERENCES git_repositories (id),
    CONSTRAINT fk_pipeline_runs_merge_request FOREIGN KEY (merge_request_id) REFERENCES merge_requests (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'GitLab Pipeline 的标准化最终一致本地快照';

CREATE TABLE webhook_deliveries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Webhook delivery 本地稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT 'delivery 所属工作区租户范围',
    connection_id BIGINT UNSIGNED NOT NULL COMMENT '接收 delivery 的 GitLab 连接',
    delivery_key VARCHAR(128) NOT NULL COMMENT '远端 UUID 或基于事件事实计算的确定性键',
    event_type VARCHAR(128) NOT NULL COMMENT 'GitLab X-Gitlab-Event 标准事件类型',
    payload_hash CHAR(64) NOT NULL COMMENT '受限原始 payload 的 SHA-256 摘要',
    payload JSON NULL COMMENT '仅异步处理期间短期保存的受限原始 JSON；完成后清空',
    status VARCHAR(24) NOT NULL COMMENT 'PENDING、PROCESSING、PROCESSED、IGNORED、FAILED 或 DEAD',
    attempts INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已失败处理次数；达到上限后转 DEAD',
    next_attempt_at DATETIME(6) NOT NULL COMMENT '下次可领取时间或 PROCESSING 租约到期时间',
    error_message VARCHAR(512) NULL COMMENT '最近一次失败的截断描述；不得包含原始 payload',
    received_at DATETIME(6) NOT NULL COMMENT 'ForgeAI 首次接收 delivery 的 UTC 时间',
    processed_at DATETIME(6) NULL COMMENT '成功处理或安全忽略的 UTC 时间；待处理时为空',
    PRIMARY KEY (id),
    UNIQUE KEY uq_webhook_deliveries_connection_key (connection_id, delivery_key),
    KEY idx_webhook_deliveries_claim (status, next_attempt_at, id),
    CONSTRAINT fk_webhook_deliveries_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_webhook_deliveries_connection FOREIGN KEY (connection_id) REFERENCES gitlab_connections (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '至少一次接收、幂等领取与退避处理的 GitLab Webhook 队列';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('repo.write', 'repo', 'write', '触发项目绑定仓库的 Pipeline 等受控远端写操作')
ON DUPLICATE KEY UPDATE resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'repo.write'
WHERE r.code IN ('OWNER', 'ADMIN', 'DEVELOPER');
