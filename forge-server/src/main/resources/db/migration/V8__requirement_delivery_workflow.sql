CREATE TABLE requirement_executors (
    organization_id BIGINT UNSIGNED NOT NULL COMMENT '人员关联所属公司，所有查询必须限定公司',
    requirement_id BIGINT UNSIGNED NOT NULL COMMENT '执行人参与的需求标识',
    role_code VARCHAR(32) NOT NULL COMMENT '执行领域：PRODUCT、UX、DEVELOPER或QA',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '具有对应领域角色的有效执行人',
    assigned_by BIGINT UNSIGNED NOT NULL COMMENT '为该执行人分配工作的领域负责人',
    created_at DATETIME(6) NOT NULL COMMENT '关联建立的UTC时间',
    PRIMARY KEY (organization_id, requirement_id, role_code, user_id),
    CONSTRAINT fk_requirement_executors_requirement FOREIGN KEY (requirement_id) REFERENCES work_items(id) ON DELETE CASCADE,
    CONSTRAINT chk_requirement_executors_role CHECK (role_code IN ('PRODUCT','UX','DEVELOPER','QA'))
) COMMENT='需求各领域的执行人，由对应需求负责人维护';

CREATE TABLE requirement_review_votes (
    organization_id BIGINT UNSIGNED NOT NULL COMMENT '评审所属公司作用域',
    requirement_id BIGINT UNSIGNED NOT NULL COMMENT '正在进行四方会签的需求',
    role_code VARCHAR(32) NOT NULL COMMENT '本次通过结论所代表的领域',
    reviewer_user_id BIGINT UNSIGNED NOT NULL COMMENT '当前该领域负责人，记录实际确认人',
    created_at DATETIME(6) NOT NULL COMMENT '本轮确认通过的UTC时间',
    PRIMARY KEY (organization_id, requirement_id, role_code),
    CONSTRAINT fk_requirement_review_votes_requirement FOREIGN KEY (requirement_id) REFERENCES work_items(id) ON DELETE CASCADE
) COMMENT='当前评审轮次的四方会签；重启评审清空，历史保留在活动事件中';

UPDATE work_items SET status = CASE
    WHEN status IN ('OPEN','REOPENED') THEN 'TODO'
    WHEN status IN ('IN_REVIEW','RESOLVED') THEN 'WAITING_VERIFICATION'
    WHEN status IN ('VERIFIED','CLOSED') THEN 'DONE'
    WHEN status = 'CANCELLED' THEN 'TODO'
    ELSE status END
WHERE type IN ('UX_TASK','DEV_TASK','QA_TASK','BUG');
UPDATE work_items wi JOIN bug_details b ON b.work_item_id=wi.id AND b.organization_id=wi.organization_id
SET wi.parent_id=b.requirement_id WHERE wi.type='BUG' AND wi.parent_id IS NULL;
DELETE bp FROM board_positions bp JOIN work_items wi ON wi.id=bp.work_item_id
WHERE wi.type IN ('UX_TASK','DEV_TASK','QA_TASK','BUG');

-- 统一测试任务使用 task 权限，最终仍须满足需求内测试负责人或执行人身份。
INSERT IGNORE INTO role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM roles r JOIN permissions p ON p.code IN ('task.create','task.edit','task.read')
WHERE r.organization_id IS NULL AND r.code IN ('QA','QA_ADMIN');
