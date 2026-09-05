CREATE TABLE branches (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '本地分支快照稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '分支所属工作区，所有查询必须显式携带',
    repository_id BIGINT UNSIGNED NOT NULL COMMENT '分支所属 Git 仓库绑定标识',
    work_item_id BIGINT UNSIGNED NULL COMMENT '关联 Dev Task；仅同步未关联分支时为空',
    name VARCHAR(255) NOT NULL COMMENT 'GitLab 仓库内的完整分支名',
    commit_sha VARCHAR(64) NOT NULL COMMENT '最近一次从远端确认的分支头提交 SHA',
    status VARCHAR(32) NOT NULL COMMENT '本地标准化缓存状态，远端事实优先',
    remote_updated_at DATETIME(6) NOT NULL COMMENT '远端资源更新时间；GitLab 未提供时为观察时间',
    last_synced_at DATETIME(6) NOT NULL COMMENT 'ForgeAI 最近完成远端核对的 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_branches_repository_name (repository_id, name),
    KEY idx_branches_workspace_work_item (workspace_id, work_item_id),
    CONSTRAINT fk_branches_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_branches_repository FOREIGN KEY (repository_id) REFERENCES git_repositories (id),
    CONSTRAINT fk_branches_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'GitLab 分支的标准化本地缓存及 Dev Task 关联';

CREATE TABLE merge_requests (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '本地 MR 快照稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT 'MR 所属工作区租户范围',
    repository_id BIGINT UNSIGNED NOT NULL COMMENT 'MR 所属 Git 仓库绑定标识',
    work_item_id BIGINT UNSIGNED NULL COMMENT '关联 Dev Task；未关联同步记录时为空',
    remote_mr_iid BIGINT UNSIGNED NOT NULL COMMENT 'GitLab 在项目内分配的 MR IID',
    title VARCHAR(255) NOT NULL COMMENT '最近一次同步的 MR 标题',
    source_branch VARCHAR(255) NOT NULL COMMENT 'MR 源分支',
    target_branch VARCHAR(255) NOT NULL COMMENT 'MR 目标分支',
    state VARCHAR(32) NOT NULL COMMENT 'GitLab 返回的标准化 MR 状态快照',
    web_url VARCHAR(1000) NOT NULL COMMENT '不含凭据的 GitLab MR 页面地址',
    author_external_id VARCHAR(128) NULL COMMENT 'GitLab 作者外部标识；无法获得时为空',
    head_sha VARCHAR(64) NULL COMMENT '最近同步的源分支头 SHA；远端未返回时为空',
    merge_status VARCHAR(64) NULL COMMENT 'GitLab 合并检查状态；远端未计算时为空',
    remote_updated_at DATETIME(6) NOT NULL COMMENT 'GitLab 返回的 MR 更新时间',
    last_synced_at DATETIME(6) NOT NULL COMMENT 'ForgeAI 最近完成远端核对的 UTC 时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '本地关联更新使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_merge_requests_repository_iid (repository_id, remote_mr_iid),
    KEY idx_merge_requests_workspace_work_item (workspace_id, work_item_id),
    CONSTRAINT fk_merge_requests_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_merge_requests_repository FOREIGN KEY (repository_id) REFERENCES git_repositories (id),
    CONSTRAINT fk_merge_requests_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'GitLab Merge Request 标准化缓存及 Dev Task 关联';

CREATE TABLE source_control_operations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '外部写操作本地稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '操作所属工作区租户范围',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '操作所属 ForgeAI 项目',
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT '触发操作的 Dev Task',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型；本轮为 START_DEVELOPMENT',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '调用方提供的项目范围幂等键',
    request_hash VARCHAR(64) NOT NULL COMMENT '用于拒绝同键不同参数的 SHA-256 摘要',
    status VARCHAR(32) NOT NULL COMMENT 'PROCESSING、COMPLETED 或 FAILED',
    branch_id BIGINT UNSIGNED NULL COMMENT '完成后关联的本地分支快照',
    merge_request_id BIGINT UNSIGNED NULL COMMENT '完成后关联的本地 MR 快照',
    created_at DATETIME(6) NOT NULL COMMENT '首次记录外部写意图的 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '操作状态最近变化的 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_source_control_operation_project_key (project_id, idempotency_key),
    KEY idx_source_control_operation_workspace_item (workspace_id, work_item_id),
    CONSTRAINT fk_source_control_operation_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_source_control_operation_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_source_control_operation_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_source_control_operation_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_source_control_operation_mr FOREIGN KEY (merge_request_id) REFERENCES merge_requests (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '不支持远端强幂等时用于 reconcile 的外部写意图与结果';
