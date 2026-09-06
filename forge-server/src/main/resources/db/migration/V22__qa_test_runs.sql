CREATE TABLE test_cases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '测试用例递增标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '用例所属工作区，用于租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '用例所属项目，用于项目授权',
    work_item_id BIGINT UNSIGNED NOT NULL COMMENT '用例覆盖的 Requirement 标识',
    title VARCHAR(255) NOT NULL COMMENT '可读且可定位的用例标题',
    preconditions TEXT NOT NULL COMMENT '执行前必须具备的条件；无条件时为空字符串',
    steps_json JSON NOT NULL COMMENT '按执行顺序保存的非空测试步骤数组',
    expected_result TEXT NOT NULL COMMENT '验证通过时应观察到的结果',
    priority VARCHAR(8) NOT NULL COMMENT 'QA 优先级：P0、P1 或 P2',
    status VARCHAR(16) NOT NULL COMMENT '用例设计状态：ACTIVE 或 ARCHIVED',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建用例的用户标识',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用例编辑使用的乐观锁版本',
    created_at DATETIME(6) NOT NULL COMMENT '用例创建 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '用例最近更新 UTC 时间',
    PRIMARY KEY (id),
    KEY idx_test_cases_work_item_status (work_item_id, status),
    CONSTRAINT fk_test_cases_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_test_cases_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_test_cases_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_test_cases_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Requirement 的可复用测试设计';

CREATE TABLE test_runs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '一次测试执行的递增标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '运行所属工作区，用于租户隔离',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '运行所属项目，用于项目授权',
    requirement_id BIGINT UNSIGNED NOT NULL COMMENT '本次验证的 Requirement 标识',
    environment VARCHAR(255) NOT NULL COMMENT '执行环境的可审计说明',
    status VARCHAR(16) NOT NULL COMMENT '运行状态：DRAFT、IN_PROGRESS、COMPLETED 或 CANCELLED',
    started_by BIGINT UNSIGNED NOT NULL COMMENT '发起运行的 QA 用户标识',
    started_at DATETIME(6) NOT NULL COMMENT '运行创建并开始的 UTC 时间',
    finished_at DATETIME(6) NULL COMMENT '完成或取消 UTC 时间；进行中为空',
    summary_json JSON NULL COMMENT '完成时固化的统计；reopen 后清空并重新计算',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '运行状态变更使用的乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_test_runs_requirement_status (requirement_id, status),
    CONSTRAINT fk_test_runs_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_test_runs_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_test_runs_requirement FOREIGN KEY (requirement_id) REFERENCES work_items (id),
    CONSTRAINT fk_test_runs_starter FOREIGN KEY (started_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '绑定一次执行集合及完成统计的测试运行';

CREATE TABLE test_results (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '单个用例在一次运行中的结果标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '结果所属工作区，用于租户隔离',
    test_run_id BIGINT UNSIGNED NOT NULL COMMENT '结果所属的测试运行标识',
    test_case_id BIGINT UNSIGNED NOT NULL COMMENT '被执行的测试用例标识',
    status VARCHAR(16) NOT NULL COMMENT '结果状态：NOT_RUN、PASS、FAIL、BLOCKED 或 SKIPPED',
    actual_result TEXT NOT NULL COMMENT '实际观察结果；未执行时为空字符串',
    evidence_json JSON NOT NULL COMMENT '证据 URL 或引用的字符串数组',
    executed_by BIGINT UNSIGNED NULL COMMENT '最后执行或修改结果的用户；未执行时为空',
    executed_at DATETIME(6) NULL COMMENT '最后执行 UTC 时间；未执行时为空',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '并发结果更新使用的乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_test_results_run_case (test_run_id, test_case_id),
    CONSTRAINT fk_test_results_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_test_results_run FOREIGN KEY (test_run_id) REFERENCES test_runs (id),
    CONSTRAINT fk_test_results_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id),
    CONSTRAINT fk_test_results_executor FOREIGN KEY (executed_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Test Case 在特定 Test Run 中的一次执行事实';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('qa.manage', 'qa', 'manage', '创建项目范围内的测试用例与测试运行'),
    ('qa.execute', 'qa', 'execute', '执行测试、完成或重新打开测试运行'),
    ('qa.read', 'qa', 'read', '读取项目范围内的测试用例与运行汇总')
ON DUPLICATE KEY UPDATE
    resource = VALUES(resource),
    action = VALUES(action),
    description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('qa.manage', 'qa.execute', 'qa.read')
WHERE r.code IN ('OWNER', 'ADMIN', 'QA');

