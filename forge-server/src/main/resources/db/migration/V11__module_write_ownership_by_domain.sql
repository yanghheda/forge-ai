-- 需求交付五个模块（PRD 文档、UX 设计、开发、QA 测试、发布）的读写权限按领域收敛（ADR-026）。
-- 目标：五个模块所有系统角色都可读；写入只保留给该模块的基础角色与领域管理员。
-- Owner 与全局管理员不再持有任何模块写入权限，只保留成员治理、外部集成、Agent 运行与高风险审批能力。

-- 一、补齐按模块拆分的权限码。
-- document.* 原先同时覆盖 PRD 与 UX 交付文档，task.* 原先同时覆盖开发与测试任务；
-- 不拆码就无法表达「PRD 只归产品、UX Spec 只归设计、测试任务只归测试」的模块归属。
INSERT INTO `permissions` (`code`, `resource`, `action`, `description`) VALUES
  ('ux.document.create', 'ux.document', 'create', '在产品需求下创建 UX 交付文档（UX Spec、原型、设计规范）'),
  ('ux.document.edit', 'ux.document', 'edit', '保存 UX 交付文档的新不可变版本'),
  ('ux.document.publish', 'ux.document', 'publish', '发布指定 UX 交付文档版本，作为 UX 评审准入材料'),
  ('development.document.create', 'development.document', 'create', '在产品需求下创建技术设计文档'),
  ('development.document.edit', 'development.document', 'edit', '保存技术设计文档的新不可变版本'),
  ('development.document.publish', 'development.document', 'publish', '发布指定技术设计文档版本'),
  ('qa.create', 'qa', 'create', '在公司范围内创建测试任务'),
  ('qa.edit', 'qa', 'edit', '修改测试任务的基础字段');

-- 二、收回越权的模块写权限。
-- 领域管理员的权限是创建时点快照，不会随基础角色变化自动增减，因此必须逐角色显式收回；
-- 只处理系统角色，不触碰公司自定义角色。requirement.create 是需求列表与仪表盘的全局入口，
-- 前端未按模块门禁，故保留给 Owner、全局管理员与产品域，不在本次收回范围。
-- requirement.approve 与 ux.approve 是审核负责人专属动作（ADR-022、ADR-024），本次把 Owner 与全局管理员
-- 也一并收回，只保留给 PRODUCT_ADMIN 与 UX_ADMIN，基础角色不因模块写权限而获得审核能力。
DELETE rp FROM `role_permissions` rp
JOIN `roles` r ON r.`id` = rp.`role_id`
JOIN `permissions` p ON p.`id` = rp.`permission_id`
WHERE r.`organization_id` IS NULL AND r.`system_role` = TRUE
  AND p.`code` IN (
    'requirement.edit', 'requirement.approve', 'requirement.accept',
    'document.create', 'document.edit', 'document.publish',
    'ux.create', 'ux.edit', 'ux.approve',
    'ux.document.create', 'ux.document.edit', 'ux.document.publish',
    'task.create', 'task.edit', 'development.submit', 'repo.write',
    'development.document.create', 'development.document.edit', 'development.document.publish',
    'qa.create', 'qa.edit', 'qa.manage', 'qa.execute',
    'bug.create', 'bug.edit', 'bug.verify', 'bug.resolve',
    'release.manage', 'release.precheck', 'release.deploy')
  AND NOT (
    (r.`code` IN ('PRODUCT', 'PRODUCT_ADMIN')
      AND p.`code` IN ('requirement.edit', 'requirement.accept',
                       'document.create', 'document.edit', 'document.publish'))
    OR (r.`code` = 'PRODUCT_ADMIN' AND p.`code` = 'requirement.approve')
    OR (r.`code` IN ('UX', 'UX_ADMIN')
      AND p.`code` IN ('ux.create', 'ux.edit',
                       'ux.document.create', 'ux.document.edit', 'ux.document.publish'))
    OR (r.`code` = 'UX_ADMIN' AND p.`code` = 'ux.approve')
    OR (r.`code` IN ('DEVELOPER', 'DEVELOPER_ADMIN')
      AND p.`code` IN ('task.create', 'task.edit', 'development.submit', 'repo.write',
                       'development.document.create', 'development.document.edit',
                       'development.document.publish', 'bug.resolve',
                       'release.manage', 'release.precheck', 'release.deploy'))
    OR (r.`code` IN ('QA', 'QA_ADMIN')
      AND p.`code` IN ('qa.create', 'qa.edit', 'qa.manage', 'qa.execute',
                       'bug.create', 'bug.edit', 'bug.verify'))
  );

-- 三、读取：五个模块的读取权限对所有系统角色开放。
-- 缺失的只有测试、缺陷、仓库与发布的读取；文档、需求、UX 与任务的读取在基线上已经是全员。
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` IN ('qa.read', 'bug.read', 'repo.read', 'release.read')
WHERE r.`organization_id` IS NULL AND r.`system_role` = TRUE;

-- 四、写入：按模块授予基础角色与领域管理员。
-- Owner 与全局管理员不出现在任何模块写入名单内，与前端 canEditModule 的模块判定保持一致。
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON (
    (r.`code` IN ('PRODUCT', 'PRODUCT_ADMIN')
      AND p.`code` IN ('requirement.edit', 'requirement.accept',
                       'document.create', 'document.edit', 'document.publish'))
    OR (r.`code` = 'PRODUCT_ADMIN' AND p.`code` = 'requirement.approve')
    OR (r.`code` IN ('UX', 'UX_ADMIN')
      AND p.`code` IN ('ux.create', 'ux.edit',
                       'ux.document.create', 'ux.document.edit', 'ux.document.publish'))
    OR (r.`code` = 'UX_ADMIN' AND p.`code` = 'ux.approve')
    OR (r.`code` IN ('DEVELOPER', 'DEVELOPER_ADMIN')
      AND p.`code` IN ('task.create', 'task.edit', 'development.submit', 'repo.write',
                       'development.document.create', 'development.document.edit',
                       'development.document.publish', 'bug.resolve',
                       'release.manage', 'release.precheck', 'release.deploy'))
    OR (r.`code` IN ('QA', 'QA_ADMIN')
      AND p.`code` IN ('qa.create', 'qa.edit', 'qa.manage', 'qa.execute',
                       'bug.create', 'bug.edit', 'bug.verify'))
)
WHERE r.`organization_id` IS NULL AND r.`system_role` = TRUE;
