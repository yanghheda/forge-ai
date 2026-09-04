CREATE TABLE document_index_jobs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文档索引任务稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；领取与状态更新必须显式限制',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目；冗余自文档事实，用于范围统计',
    document_id BIGINT UNSIGNED NOT NULL COMMENT '待索引的文档标识',
    version_id BIGINT UNSIGNED NOT NULL COMMENT '待索引的不可变文档版本标识',
    status VARCHAR(24) NOT NULL COMMENT '任务状态：PENDING 待处理、INDEXING 已领取、SUCCEEDED 已完成、FAILED 待重试、DEAD 超过重试上限待人工处理',
    attempts INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已失败尝试次数；达到上限后任务转 DEAD',
    next_attempt_at DATETIME(6) NOT NULL COMMENT '下次允许领取的 UTC 时间；FAILED 退避与 INDEXING 租约共用',
    chunk_count INT UNSIGNED NULL COMMENT '成功索引的切片数量；未成功时为空',
    error_code VARCHAR(64) NULL COMMENT '最近一次失败的稳定错误代码；成功时为空',
    error_message VARCHAR(512) NULL COMMENT '最近一次失败的截断描述；不含文档正文',
    indexed_at DATETIME(6) NULL COMMENT '索引成功完成的 UTC 时间；未成功时为空',
    created_at DATETIME(6) NOT NULL COMMENT '任务创建 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '任务状态最近变更 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_document_index_jobs_version (document_id, version_id),
    KEY idx_document_index_jobs_claim (status, next_attempt_at),
    CONSTRAINT fk_document_index_jobs_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_document_index_jobs_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_document_index_jobs_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_document_index_jobs_version FOREIGN KEY (version_id) REFERENCES document_versions (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Outbox 事件派生的文档索引重试队列；MySQL 是事实来源，Qdrant 仅是可重建的派生索引';
