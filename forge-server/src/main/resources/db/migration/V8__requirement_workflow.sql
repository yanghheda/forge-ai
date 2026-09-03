CREATE TABLE requirement_details (
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT '对应 Requirement Work Item 的稳定标识且同时作为主键',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；Guard 查询必须显式匹配该租户范围',
    goal LONGTEXT NOT NULL COMMENT '需求希望达成的业务目标；空字符串表示材料尚未满足提交条件',
    in_scope LONGTEXT NOT NULL COMMENT '本次需求明确包含的范围；空字符串表示材料尚未满足提交条件',
    out_of_scope LONGTEXT NOT NULL COMMENT '本次需求明确排除的范围；允许空字符串表示尚无排除项',
    acceptance_criteria_json JSON NOT NULL COMMENT '结构化验收标准数组；空数组表示材料尚未满足提交条件',
    business_value LONGTEXT NOT NULL COMMENT '需求的业务价值说明；允许空字符串表示尚未补充',
    updated_at DATETIME(6) NOT NULL COMMENT 'Requirement 结构化材料最近更新时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Requirement 结构化材料编辑使用的乐观锁版本',
    PRIMARY KEY (work_item_id),
    KEY idx_requirement_details_workspace (workspace_id, work_item_id),
    CONSTRAINT fk_requirement_details_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_requirement_details_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Requirement 的结构化产品材料；作为确定性产品评审 Guard 的事实来源';

CREATE TABLE review_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评审记录的稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；所有查询必须显式携带该租户范围',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目；用于授权和项目范围隔离',
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT '本次评审对应的 Requirement Work Item 标识',
    review_type VARCHAR(40) NOT NULL COMMENT '评审类型；本轮写入 PRODUCT_REVIEW',
    status VARCHAR(24) NOT NULL COMMENT '评审状态；SUBMITTED 表示等待产品评审，REJECTED 表示已退回',
    reviewer_user_id BIGINT UNSIGNED NULL COMMENT '作出评审结论的用户；仅提交等待评审时为空',
    comment VARCHAR(1000) NULL COMMENT '评审意见；没有意见时为空',
    checklist_json JSON NOT NULL COMMENT '评审时固化的结构化检查清单；本轮产品提交使用空对象',
    artifact_version_json JSON NOT NULL COMMENT '评审引用的交付物版本快照；本轮尚无文档版本时使用空对象',
    created_at DATETIME(6) NOT NULL COMMENT '评审记录创建的 UTC 时间',
    PRIMARY KEY (id),
    KEY idx_review_records_work_item (work_item_id, review_type, status, id),
    CONSTRAINT fk_review_records_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_review_records_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_review_records_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_review_records_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Requirement 产品与体验评审的不可变结论记录';

CREATE TABLE work_item_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作项活动事件的单调标识；同一工作项按此字段排序',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；事件查询必须显式匹配该租户范围',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目；事件查询必须显式匹配该项目范围',
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT '事件所属的 Work Item 聚合标识',
    event_type VARCHAR(64) NOT NULL COMMENT '稳定的机器可读事件类型；本轮使用工作流 Action 名称',
    from_status VARCHAR(40) NOT NULL COMMENT '动作执行前的主生命周期状态',
    to_status VARCHAR(40) NOT NULL COMMENT '动作成功后的主生命周期状态',
    actor_type VARCHAR(16) NOT NULL COMMENT '动作主体类型；本轮公开 API 固定为 USER',
    actor_id BIGINT UNSIGNED NOT NULL COMMENT '执行动作的用户标识',
    reason VARCHAR(1000) NULL COMMENT '动作原因；不要求原因的动作可以为空',
    metadata_json JSON NOT NULL COMMENT '事件附加机器数据；没有附加数据时使用空对象',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '调用方提供的重试标识；同一 Work Item 内不得复用',
    created_at DATETIME(6) NOT NULL COMMENT '事件与状态转换在同一事务内成功的 UTC 时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_work_item_events_idempotency (work_item_id, idempotency_key),
    KEY idx_work_item_events_timeline (work_item_id, id),
    CONSTRAINT fk_work_item_events_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_work_item_events_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_work_item_events_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_work_item_events_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Work Item 状态转换的追加写活动历史；当前状态仍以 work_items 为事实源';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('requirement.review', 'requirement', 'review', '执行 Requirement 产品评审动作');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'ADMIN', 'PRODUCT') AND p.code = 'requirement.review';
