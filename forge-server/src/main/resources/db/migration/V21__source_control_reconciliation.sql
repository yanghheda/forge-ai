ALTER TABLE source_control_operations
    ADD COLUMN target_branch VARCHAR(255) NOT NULL DEFAULT '' COMMENT '启动开发请求冻结的目标分支；恢复时不得由当前仓库默认值漂移' AFTER request_hash,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 COMMENT '后台恢复尝试次数；不含原始同步请求' AFTER status,
    ADD COLUMN next_attempt_at DATETIME(6) NULL COMMENT '下次允许恢复的 UTC 时间；为空表示可立即检查' AFTER attempt_count,
    ADD COLUMN last_error_code VARCHAR(64) NULL COMMENT '最近一次标准化 GitLab 错误码；不得保存 Token 或远端正文' AFTER next_attempt_at,
    ADD KEY idx_source_control_operations_reconcile (status, next_attempt_at, updated_at);
