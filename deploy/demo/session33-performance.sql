START TRANSACTION;

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO users (
    id, email, normalized_email, display_name, password_hash, status,
    failed_login_count, created_at, updated_at, version
) VALUES (
    32001, 'owner@perf.forgeai.local', 'owner@perf.forgeai.local', 'Performance Owner',
    '$2y$12$Xb3evs9u9Iv7E/DqtdSB7eoAAKt/UNGuFMTFXETHdRIvMGQEo2Kdi', 'ACTIVE', 0,
    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), password_hash = VALUES(password_hash), status = 'ACTIVE';

INSERT INTO organizations (id, name, slug, owner_user_id, created_at, updated_at, version)
VALUES (32003, 'ForgeAI Performance', 'forgeai-performance', 32001, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), owner_user_id = VALUES(owner_user_id);

INSERT INTO organization_members (
    id, organization_id, user_id, status, joined_at, created_at, updated_at, version
) VALUES (
    32005, 32003, 32001, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
)
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT IGNORE INTO member_roles (organization_member_id, role_id, created_at)
SELECT 32005, id, UTC_TIMESTAMP(6) FROM roles WHERE code = 'OWNER';

INSERT INTO organization_item_sequences (organization_id, next_value, version)
VALUES (32003, 10001, 0)
ON DUPLICATE KEY UPDATE next_value = GREATEST(next_value, 10001), version = version + 1;

INSERT INTO work_items (
    organization_id,
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
    32003,
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
    SELECT ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 AS sequence_number
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
