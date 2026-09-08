START TRANSACTION;

INSERT INTO projects (
    id,
    workspace_id,
    `key`,
    name,
    description,
    status,
    created_by,
    created_at,
    updated_at,
    version
) VALUES (
    33007,
    32004,
    'PERF',
    'Session 33 Performance',
    '10k Work Item 分页与索引验证专用项目',
    'ACTIVE',
    32001,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    0
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = 'ACTIVE';

INSERT INTO project_members (
    id,
    workspace_id,
    project_id,
    user_id,
    status,
    created_at,
    updated_at
) VALUES (
    33008,
    32004,
    33007,
    32001,
    'ACTIVE',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
) ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO project_item_sequences (id, project_id, next_value, version)
VALUES (33010, 33007, 10001, 0)
ON DUPLICATE KEY UPDATE next_value = GREATEST(next_value, 10001);

INSERT INTO work_items (
    workspace_id,
    project_id,
    item_number,
    item_key,
    type,
    title,
    description,
    status,
    priority,
    parent_id,
    assignee_user_id,
    reporter_user_id,
    due_at,
    severity,
    blocked_at,
    blocked_reason,
    blocked_by,
    created_at,
    updated_at,
    deleted_at,
    version
)
SELECT
    32004,
    33007,
    sequence_number + 1,
    CONCAT('PERF-', sequence_number + 1),
    IF(MOD(sequence_number, 4) = 0, 'REQUIREMENT', 'DEV_TASK'),
    CONCAT('[PERF] Work Item ', sequence_number + 1),
    REPEAT('x', 4096),
    IF(MOD(sequence_number, 5) = 0, 'IN_PROGRESS', 'TODO'),
    'MEDIUM',
    NULL,
    32001,
    32001,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    NULL,
    0
FROM (
    SELECT
        ones.n
        + tens.n * 10
        + hundreds.n * 100
        + thousands.n * 1000 AS sequence_number
    FROM (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) ones
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) tens
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) hundreds
    CROSS JOIN (
        SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ) thousands
) numbers
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    status = VALUES(status),
    updated_at = UTC_TIMESTAMP(6);

COMMIT;
