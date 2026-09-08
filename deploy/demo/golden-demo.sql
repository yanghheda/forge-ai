-- ForgeAI 会话 32 黄金 Demo。固定 ID 只占用 32000-32999，重复执行会收敛到同一组事实。
START TRANSACTION;

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO users (
    id, email, normalized_email, display_name, password_hash, status,
    failed_login_count, created_at, updated_at, version
) VALUES
    (32001, 'owner@demo.forgeai.local', 'owner@demo.forgeai.local', 'Demo Owner',
     '$2y$12$xVMAooMUaBYCMdlpstsZneMPTQU64UyB8wbBOHICnzfRtsA41dsEm', 'ACTIVE', 0,
     '2026-01-01 08:00:00.000000', '2026-01-01 08:00:00.000000', 0),
    (32002, 'approver@demo.forgeai.local', 'approver@demo.forgeai.local', 'Demo Approver',
     '$2y$12$xVMAooMUaBYCMdlpstsZneMPTQU64UyB8wbBOHICnzfRtsA41dsEm', 'ACTIVE', 0,
     '2026-01-01 08:00:00.000000', '2026-01-01 08:00:00.000000', 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), password_hash = VALUES(password_hash), status = 'ACTIVE';

INSERT INTO organizations (id, name, slug, owner_user_id, created_at, updated_at, version)
VALUES (32003, 'ForgeAI Demo', 'forgeai-demo', 32001,
        '2026-01-01 08:00:00.000000', '2026-01-01 08:00:00.000000', 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), owner_user_id = VALUES(owner_user_id);

INSERT INTO workspaces (
    id, organization_id, name, slug, status, settings_json, created_at, updated_at, version
) VALUES (
    32004, 32003, 'Demo Workspace', 'demo', 'ACTIVE', JSON_OBJECT('fixture', 'golden-demo-v1'),
    '2026-01-01 08:00:00.000000', '2026-01-01 08:00:00.000000', 0
)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 'ACTIVE', settings_json = VALUES(settings_json);

INSERT INTO workspace_members (
    id, workspace_id, user_id, status, joined_at, created_at, updated_at, version
) VALUES
    (32005, 32004, 32001, 'ACTIVE', '2026-01-01 08:00:00.000000',
     '2026-01-01 08:00:00.000000', '2026-01-01 08:00:00.000000', 0),
    (32006, 32004, 32002, 'ACTIVE', '2026-01-01 08:00:00.000000',
     '2026-01-01 08:00:00.000000', '2026-01-01 08:00:00.000000', 0)
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT IGNORE INTO member_roles (workspace_member_id, role_id, project_id, created_at)
SELECT 32005, id, NULL, '2026-01-01 08:00:00.000000' FROM roles WHERE code = 'OWNER';
INSERT IGNORE INTO member_roles (workspace_member_id, role_id, project_id, created_at)
SELECT 32006, id, NULL, '2026-01-01 08:00:00.000000' FROM roles WHERE code = 'ADMIN';

INSERT INTO projects (
    id, workspace_id, `key`, name, description, status, created_by, created_at, updated_at, version
) VALUES (
    32007, 32004, 'DEMO', 'Demo Shop', '手机号验证码登录黄金交付闭环', 'ACTIVE', 32001,
    '2026-01-01 08:01:00.000000', '2026-01-01 08:01:00.000000', 0
)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), status = 'ACTIVE';

INSERT INTO project_members (id, workspace_id, project_id, user_id, status, created_at, updated_at)
VALUES
    (32008, 32004, 32007, 32001, 'ACTIVE', '2026-01-01 08:01:00.000000', '2026-01-01 08:01:00.000000'),
    (32009, 32004, 32007, 32002, 'ACTIVE', '2026-01-01 08:01:00.000000', '2026-01-01 08:01:00.000000')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO project_policies (
    project_id, workspace_id, allow_skip_ux, ci_required, updated_by, updated_at, version
) VALUES (32007, 32004, FALSE, TRUE, 32001, '2026-01-01 08:01:00.000000', 1)
ON DUPLICATE KEY UPDATE allow_skip_ux = FALSE, ci_required = TRUE, updated_by = 32001, version = 1;

INSERT INTO project_item_sequences (id, project_id, next_value, version)
VALUES (32010, 32007, 6, 0)
ON DUPLICATE KEY UPDATE next_value = GREATEST(next_value, 6);

INSERT INTO work_items (
    id, workspace_id, project_id, item_number, item_key, type, title, description, status,
    priority, parent_id, assignee_user_id, reporter_user_id, severity, created_at, updated_at, version
) VALUES
    (32011, 32004, 32007, 1, 'DEMO-1', 'REQUIREMENT', '增加手机号验证码登录',
     '用户可通过手机号和一次性验证码安全登录。', 'DONE', 'HIGH', NULL, 32001, 32001, NULL,
     '2026-01-01 08:02:00.000000', '2026-01-01 10:00:00.000000', 9),
    (32012, 32004, 32007, 2, 'DEMO-2', 'UX_TASK', '手机号登录体验设计',
     '覆盖正常、无效号码、错误验证码和重试状态。', 'DONE', 'HIGH', 32011, 32001, 32001, NULL,
     '2026-01-01 08:10:00.000000', '2026-01-01 08:30:00.000000', 3),
    (32013, 32004, 32007, 3, 'DEMO-3', 'DEV_TASK', '实现手机号验证码登录',
     '实现服务端验证码校验和 Web 登录表单。', 'DONE', 'HIGH', 32011, 32001, 32001, NULL,
     '2026-01-01 08:35:00.000000', '2026-01-01 09:10:00.000000', 3),
    (32014, 32004, 32007, 4, 'DEMO-4', 'QA_TASK', '执行手机号登录回归',
     '执行正常与异常登录路径。', 'DONE', 'HIGH', 32011, 32002, 32001, NULL,
     '2026-01-01 09:15:00.000000', '2026-01-01 09:40:00.000000', 2),
    (32015, 32004, 32007, 5, 'DEMO-5', 'BUG', '验证码重试后按钮未恢复',
     '首次回归发现，修复后独立验证关闭。', 'CLOSED', 'HIGH', 32011, 32001, 32002, 'MAJOR',
     '2026-01-01 09:25:00.000000', '2026-01-01 09:45:00.000000', 4)
ON DUPLICATE KEY UPDATE
    title = VALUES(title), description = VALUES(description), status = VALUES(status),
    priority = VALUES(priority), parent_id = VALUES(parent_id), assignee_user_id = VALUES(assignee_user_id),
    severity = VALUES(severity), version = VALUES(version);

INSERT INTO requirement_details (
    work_item_id, workspace_id, goal, in_scope, out_of_scope,
    acceptance_criteria_json, business_value, updated_at, version
) VALUES (
    32011, 32004, '降低密码登录摩擦并保持账号安全',
    '中国大陆手机号、一次性验证码、错误与重试状态', '第三方社交登录与国际短信',
    JSON_ARRAY('合法手机号可获取验证码', '正确验证码可登录', '错误验证码不会创建会话'),
    '提升新用户转化率', '2026-01-01 08:05:00.000000', 1
)
ON DUPLICATE KEY UPDATE
    goal = VALUES(goal), in_scope = VALUES(in_scope), out_of_scope = VALUES(out_of_scope),
    acceptance_criteria_json = VALUES(acceptance_criteria_json), business_value = VALUES(business_value), version = 1;

INSERT INTO work_item_relations (
    id, workspace_id, project_id, source_id, target_id, relation_type, created_by, created_at
) VALUES
    (32016, 32004, 32007, 32013, 32015, 'RELATES_TO', 32001, '2026-01-01 09:25:00.000000'),
    (32017, 32004, 32007, 32014, 32015, 'RELATES_TO', 32002, '2026-01-01 09:25:00.000000')
ON DUPLICATE KEY UPDATE relation_type = VALUES(relation_type);

INSERT INTO documents (
    id, workspace_id, project_id, work_item_id, type, title, status, visibility,
    current_version_id, created_by, created_at, updated_at, version
) VALUES
    (32020, 32004, 32007, 32011, 'PRD', '手机号验证码登录 PRD', 'PUBLISHED', 'PROJECT', NULL,
     32001, '2026-01-01 08:05:00.000000', '2026-01-01 08:08:00.000000', 2),
    (32021, 32004, 32007, 32012, 'UX_SPEC', '手机号登录 UX Spec', 'PUBLISHED', 'PROJECT', NULL,
     32001, '2026-01-01 08:12:00.000000', '2026-01-01 08:25:00.000000', 2),
    (32022, 32004, 32007, 32013, 'TECH_DESIGN', '手机号登录技术方案', 'PUBLISHED', 'PROJECT', NULL,
     32001, '2026-01-01 08:36:00.000000', '2026-01-01 08:45:00.000000', 2)
ON DUPLICATE KEY UPDATE
    title = VALUES(title), status = 'PUBLISHED', current_version_id = NULL, version = VALUES(version);

INSERT INTO document_versions (
    id, workspace_id, document_id, version_no, content_format, content, content_hash,
    plain_text, summary, created_by, created_at
) VALUES
    (32030, 32004, 32020, 1, 'PROSEMIRROR_JSON', JSON_OBJECT('type', 'doc', 'content', JSON_ARRAY()),
     REPEAT('a', 64), '手机号验证码登录 PRD：目标、范围、验收标准。', '黄金 Demo PRD', 32001,
     '2026-01-01 08:08:00.000000'),
    (32031, 32004, 32021, 1, 'PROSEMIRROR_JSON', JSON_OBJECT('type', 'doc', 'content', JSON_ARRAY()),
     REPEAT('b', 64), '手机号登录 UX Spec：正常、异常和重试状态。', '黄金 Demo UX', 32001,
     '2026-01-01 08:25:00.000000'),
    (32032, 32004, 32022, 1, 'PROSEMIRROR_JSON', JSON_OBJECT('type', 'doc', 'content', JSON_ARRAY()),
     REPEAT('c', 64), '手机号登录技术方案：API、限流和会话边界。', '黄金 Demo 技术方案', 32001,
     '2026-01-01 08:45:00.000000')
ON DUPLICATE KEY UPDATE plain_text = VALUES(plain_text), summary = VALUES(summary);

UPDATE documents SET current_version_id = 32030 WHERE id = 32020;
UPDATE documents SET current_version_id = 32031 WHERE id = 32021;
UPDATE documents SET current_version_id = 32032 WHERE id = 32022;

INSERT INTO secrets (
    id, workspace_id, type, ciphertext, iv, key_version, fingerprint, created_at
) VALUES (
    32040, 32004, 'GITLAB_TOKEN', 'demo-fixture-never-decrypted', 'demo-fixture', 1,
    'demo-mock-gitlab', '2026-01-01 08:40:00.000000'
)
ON DUPLICATE KEY UPDATE fingerprint = VALUES(fingerprint);

INSERT INTO gitlab_connections (
    id, workspace_id, name, base_url, credential_secret_id, status, last_tested_at,
    created_by, created_at, updated_at, version
) VALUES (
    32041, 32004, 'Demo Mock GitLab', 'https://gitlab.demo.invalid', 32040, 'ACTIVE',
    '2026-01-01 08:40:00.000000', 32001, '2026-01-01 08:40:00.000000',
    '2026-01-01 08:40:00.000000', 0
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), base_url = VALUES(base_url), status = 'ACTIVE',
    last_tested_at = VALUES(last_tested_at);

INSERT INTO git_repositories (
    id, workspace_id, project_id, connection_id, remote_project_id, path_with_namespace,
    http_url, default_branch, status, last_synced_at, created_at, updated_at, version
) VALUES (
    32042, 32004, 32007, 32041, '32007', 'demo/demo-shop',
    'https://gitlab.demo.invalid/demo/demo-shop', 'main', 'ACTIVE',
    '2026-01-01 09:10:00.000000', '2026-01-01 08:40:00.000000',
    '2026-01-01 09:10:00.000000', 1
)
ON DUPLICATE KEY UPDATE path_with_namespace = VALUES(path_with_namespace), status = 'ACTIVE';

INSERT INTO branches (
    id, workspace_id, repository_id, work_item_id, name, commit_sha, status,
    remote_updated_at, last_synced_at
) VALUES (
    32043, 32004, 32042, 32013, 'feat/DEMO-3-phone-login', REPEAT('1', 40), 'ACTIVE',
    '2026-01-01 09:00:00.000000', '2026-01-01 09:10:00.000000'
)
ON DUPLICATE KEY UPDATE commit_sha = VALUES(commit_sha), status = 'ACTIVE';

INSERT INTO merge_requests (
    id, workspace_id, repository_id, work_item_id, remote_mr_iid, title, source_branch,
    target_branch, state, web_url, author_external_id, head_sha, merge_status,
    remote_updated_at, last_synced_at, version
) VALUES (
    32044, 32004, 32042, 32013, 18, 'feat: 手机号验证码登录',
    'feat/DEMO-3-phone-login', 'main', 'merged',
    'https://gitlab.demo.invalid/demo/demo-shop/-/merge_requests/18', 'demo-dev',
    REPEAT('1', 40), 'can_be_merged', '2026-01-01 09:10:00.000000',
    '2026-01-01 09:10:00.000000', 1
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title), source_branch = VALUES(source_branch), target_branch = VALUES(target_branch),
    state = 'merged', head_sha = VALUES(head_sha), merge_status = VALUES(merge_status);

INSERT INTO pipeline_runs (
    id, workspace_id, repository_id, merge_request_id, remote_pipeline_id, ref, commit_sha,
    status, web_url, started_at, finished_at, remote_updated_at, last_synced_at, summary_json
) VALUES (
    32045, 32004, 32042, 32044, 801, 'feat/DEMO-3-phone-login', REPEAT('1', 40), 'success',
    'https://gitlab.demo.invalid/demo/demo-shop/-/pipelines/801',
    '2026-01-01 09:05:00.000000', '2026-01-01 09:09:00.000000',
    '2026-01-01 09:09:00.000000', '2026-01-01 09:10:00.000000',
    JSON_OBJECT('passed', 12, 'failed', 0)
)
ON DUPLICATE KEY UPDATE status = 'success', commit_sha = VALUES(commit_sha), summary_json = VALUES(summary_json);

INSERT INTO test_cases (
    id, workspace_id, project_id, work_item_id, title, preconditions, steps_json,
    expected_result, priority, status, created_by, version, created_at, updated_at
) VALUES (
    32050, 32004, 32007, 32011, '正确验证码可建立会话', '手机号未注册或可自动注册',
    JSON_ARRAY('输入合法手机号', '获取验证码', '输入正确验证码', '提交登录'),
    '登录成功并进入 Demo Workspace', 'P0', 'ACTIVE', 32002, 0,
    '2026-01-01 09:15:00.000000', '2026-01-01 09:15:00.000000'
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title), preconditions = VALUES(preconditions), steps_json = VALUES(steps_json),
    expected_result = VALUES(expected_result), status = 'ACTIVE';

INSERT INTO test_runs (
    id, workspace_id, project_id, requirement_id, environment, status, started_by,
    started_at, finished_at, summary_json, version
) VALUES (
    32051, 32004, 32007, 32011, 'demo-mock', 'COMPLETED', 32002,
    '2026-01-01 09:30:00.000000', '2026-01-01 09:40:00.000000',
    JSON_OBJECT('total', 1, 'passed', 1, 'failed', 0, 'blocked', 0,
                'skipped', 0, 'notRun', 0, 'mandatorySkipped', 0), 2
)
ON DUPLICATE KEY UPDATE status = 'COMPLETED', summary_json = VALUES(summary_json), version = 2;

INSERT INTO test_results (
    id, workspace_id, test_run_id, test_case_id, status, actual_result, evidence_json,
    executed_by, executed_at, version
) VALUES (
    32052, 32004, 32051, 32050, 'PASS', '修复后验证码登录与按钮重试均通过',
    JSON_ARRAY('mock-gitlab://pipeline/801', 'demo://screenshots/login-success'),
    32002, '2026-01-01 09:39:00.000000', 2
)
ON DUPLICATE KEY UPDATE
    status = 'PASS', actual_result = VALUES(actual_result), evidence_json = VALUES(evidence_json),
    executed_by = VALUES(executed_by), executed_at = VALUES(executed_at);

INSERT INTO bug_details (
    work_item_id, workspace_id, project_id, requirement_id, test_run_id, test_result_id,
    reproduction_steps_json, expected_result, actual_result, fix_note, fix_evidence_json,
    verified_by, verified_at, version
) VALUES (
    32015, 32004, 32007, 32011, 32051, 32052,
    JSON_ARRAY('输入错误验证码', '等待提示', '点击重新获取'),
    '重新获取按钮恢复可用', '按钮保持禁用', '重置倒计时状态',
    JSON_ARRAY('https://gitlab.demo.invalid/demo/demo-shop/-/merge_requests/18'),
    32002, '2026-01-01 09:39:00.000000', 3
)
ON DUPLICATE KEY UPDATE
    fix_note = VALUES(fix_note), fix_evidence_json = VALUES(fix_evidence_json),
    verified_by = VALUES(verified_by), verified_at = VALUES(verified_at), version = 3;

INSERT INTO releases (
    id, workspace_id, project_id, version_name, environment, status, release_note,
    policy_snapshot_json, created_by, created_at, updated_at, version
) VALUES (
    32060, 32004, 32007, 'v1.0.0-demo', 'demo', 'RELEASED',
    '# v1.0.0-demo\n\n- 手机号验证码登录\n- 修复验证码重试按钮',
    JSON_OBJECT('ciRequired', TRUE, 'approvalTtlMinutes', 60), 32001,
    '2026-01-01 09:45:00.000000', '2026-01-01 10:00:00.000000', 1
)
ON DUPLICATE KEY UPDATE status = 'RELEASED', release_note = VALUES(release_note), version = 1;

INSERT INTO release_items (release_id, work_item_id, workspace_id, project_id)
VALUES (32060, 32011, 32004, 32007)
ON DUPLICATE KEY UPDATE workspace_id = VALUES(workspace_id), project_id = VALUES(project_id);

INSERT INTO release_prechecks (
    id, workspace_id, release_id, status, checks_json, checked_at,
    checked_by_type, checked_by_id, resource_versions_json
) VALUES (
    32061, 32004, 32060, 'PASS',
    JSON_ARRAY(
        JSON_OBJECT('rule', 'CI_SUCCESS', 'passed', TRUE),
        JSON_OBJECT('rule', 'QA_PASS', 'passed', TRUE),
        JSON_OBJECT('rule', 'NO_OPEN_BLOCKER', 'passed', TRUE)
    ),
    '2026-01-01 09:50:00.000000', 'USER', 32001,
    JSON_OBJECT('release', 1, 'requirement:32011', 9, 'pipeline:32045', 1, 'testRun:32051', 2)
)
ON DUPLICATE KEY UPDATE status = 'PASS', checks_json = VALUES(checks_json);

INSERT INTO deployments (
    id, workspace_id, project_id, release_id, mode, status, requested_by, approver_user_id,
    approval_expires_at, decided_at, release_version, precheck_id, argument_hash,
    simulate_failure, idempotency_key, result_code, result_summary, approval_source,
    created_at, started_at, finished_at, version
) VALUES (
    32062, 32004, 32007, 32060, 'SIMULATED', 'SUCCEEDED', 32001, 32002,
    '2026-01-01 10:50:00.000000', '2026-01-01 09:55:00.000000', 1, 32061,
    REPEAT('d', 64), FALSE, 'golden-demo-deploy-v1', 'SIMULATED_SUCCESS',
    '模拟部署成功；未访问真实生产环境。', 'HUMAN_REQUEST',
    '2026-01-01 09:52:00.000000', '2026-01-01 09:56:00.000000',
    '2026-01-01 10:00:00.000000', 3
)
ON DUPLICATE KEY UPDATE
    status = 'SUCCEEDED', result_code = VALUES(result_code), result_summary = VALUES(result_summary);

INSERT INTO agent_runs (
    id, workspace_id, project_id, work_item_id, user_id, skill, medium_tool_confirmation,
    message_redacted, client_request_id, request_hash, status, model_provider, model_name,
    prompt_version, started_at, finished_at, token_input, token_output, cost, last_sequence,
    version, created_at, updated_at
) VALUES (
    'DEMA0000000000000000000001', 32004, 32007, 32011, 32001, 'RELEASE', 'ASK',
    'golden demo message length=42', 'golden-demo-agent-v1', REPEAT('e', 64), 'SUCCEEDED',
    'mock', 'deterministic-fixture-v1', 'release-v1', '2026-01-01 09:48:00.000000',
    '2026-01-01 09:51:00.000000', 128, 64, 0, 6, 2,
    '2026-01-01 09:48:00.000000', '2026-01-01 09:51:00.000000'
)
ON DUPLICATE KEY UPDATE
    status = 'SUCCEEDED', model_provider = 'mock', model_name = 'deterministic-fixture-v1',
    last_sequence = 6, version = 2;

INSERT INTO agent_steps (
    id, run_id, step_no, type, name, status, input_summary, output_summary,
    started_at, finished_at
) VALUES
    (32070, 'DEMA0000000000000000000001', 1, 'PLAN', '读取交付事实', 'SUCCEEDED',
     'Requirement DEMO-1', 'Delivery Graph 与 Precheck 已读取',
     '2026-01-01 09:48:00.000000', '2026-01-01 09:49:00.000000'),
    (32071, 'DEMA0000000000000000000001', 2, 'TOOL', '生成 Release Note', 'SUCCEEDED',
     'Release 32060', 'Release Note 草稿已保存',
     '2026-01-01 09:49:00.000000', '2026-01-01 09:51:00.000000')
ON DUPLICATE KEY UPDATE
    type = VALUES(type), name = VALUES(name), status = 'SUCCEEDED',
    input_summary = VALUES(input_summary), output_summary = VALUES(output_summary),
    started_at = VALUES(started_at), finished_at = VALUES(finished_at);

INSERT INTO agent_events (
    id, run_id, sequence, event_type, request_id, payload_json, created_at
) VALUES
    (32072, 'DEMA0000000000000000000001', 1, 'agent.queued', 'golden-demo', JSON_OBJECT('status', 'QUEUED'), '2026-01-01 09:48:00.000000'),
    (32073, 'DEMA0000000000000000000001', 2, 'agent.started', 'golden-demo', JSON_OBJECT('status', 'RUNNING'), '2026-01-01 09:48:10.000000'),
    (32074, 'DEMA0000000000000000000001', 3, 'step.started', 'golden-demo', JSON_OBJECT('stepNo', 1), '2026-01-01 09:48:20.000000'),
    (32075, 'DEMA0000000000000000000001', 4, 'step.completed', 'golden-demo', JSON_OBJECT('stepNo', 1), '2026-01-01 09:49:00.000000'),
    (32076, 'DEMA0000000000000000000001', 5, 'tool.completed', 'golden-demo', JSON_OBJECT('toolName', 'update_release_note'), '2026-01-01 09:50:00.000000'),
    (32077, 'DEMA0000000000000000000001', 6, 'agent.completed', 'golden-demo', JSON_OBJECT('status', 'SUCCEEDED'), '2026-01-01 09:51:00.000000')
ON DUPLICATE KEY UPDATE event_type = VALUES(event_type), payload_json = VALUES(payload_json);

INSERT INTO agent_tool_calls (
    id, run_id, workspace_id, project_id, tool_call_id, tool_name, tool_version,
    risk_level, argument_hash, status, arguments_json, result_json, idempotency_key,
    created_at, finished_at
) VALUES (
    32078, 'DEMA0000000000000000000001', 32004, 32007, 'demo-release-note',
    'update_release_note', 1, 'MEDIUM', REPEAT('f', 64), 'SUCCEEDED',
    JSON_OBJECT('releaseId', 32060), JSON_OBJECT('releaseId', 32060, 'status', 'RELEASED'),
    'DEMA0000000000000000000001:demo-release-note',
    '2026-01-01 09:49:00.000000', '2026-01-01 09:50:00.000000'
)
ON DUPLICATE KEY UPDATE status = 'SUCCEEDED', result_json = VALUES(result_json);

UPDATE instance_settings
SET initialized_at = COALESCE(initialized_at, '2026-01-01 08:00:00.000000'),
    default_organization_id = COALESCE(default_organization_id, 32003),
    settings_json = JSON_SET(settings_json, '$.goldenDemoVersion', 'v1'),
    version = version + IF(JSON_EXTRACT(settings_json, '$.goldenDemoVersion') IS NULL, 1, 0)
WHERE id = 1;

COMMIT;
