-- 角色模型调整：Admin 升级为全局管理员，新增产品/UX/开发/测试四个领域管理员，
-- 并移除发布审批角色（其审批职责移交开发管理员、全局管理员与 Owner）。

-- Admin 语义升级为全局管理员，仅改展示名与描述，角色代码保持稳定。
UPDATE `roles`
SET `name` = '全局管理员',
    `description` = '全局管理员；负责成员治理、角色分配与跨领域协调，权限与 Owner 并列但不可管理 Owner'
WHERE `code` = 'ADMIN' AND `organization_id` IS NULL;

-- 新增四个领域管理员系统角色；它们只能由全局管理员或 Owner 在成员管理中分配，不支持自助注册。
INSERT INTO `roles` (`organization_id`, `code`, `name`, `system_role`, `description`) VALUES
  (NULL, 'PRODUCT_ADMIN', '产品管理员', 1, '产品领域负责人；拥有产品全部权限，负责产品阶段任务分配与需求审核'),
  (NULL, 'UX_ADMIN', 'UX 管理员', 1, 'UX 领域负责人；拥有 UX 全部权限，负责体验阶段任务分配与 UX 审核'),
  (NULL, 'DEVELOPER_ADMIN', '开发管理员', 1, '开发领域负责人；拥有研发全部权限并承接发布审批职责'),
  (NULL, 'QA_ADMIN', '测试管理员', 1, '测试领域负责人；拥有测试全部权限，负责测试阶段任务分配与质量结论');

-- 产品、UX、测试管理员继承对应基础角色的权限集合。
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT admin_role.`id`, rp.`permission_id`
FROM `roles` admin_role
JOIN `roles` base_role
  ON base_role.`organization_id` IS NULL
 AND base_role.`code` = CASE admin_role.`code`
       WHEN 'PRODUCT_ADMIN' THEN 'PRODUCT'
       WHEN 'UX_ADMIN' THEN 'UX'
       WHEN 'QA_ADMIN' THEN 'QA'
     END
JOIN `role_permissions` rp ON rp.`role_id` = base_role.`id`
WHERE admin_role.`organization_id` IS NULL
  AND admin_role.`code` IN ('PRODUCT_ADMIN', 'UX_ADMIN', 'QA_ADMIN');

-- 开发管理员 = 开发 ∪ 原发布审批，从而承接 HIGH 发布审批职责；DISTINCT 去重两个基础角色的公共权限。
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT DISTINCT admin_role.`id`, rp.`permission_id`
FROM `roles` admin_role
JOIN `roles` base_role
  ON base_role.`organization_id` IS NULL
 AND base_role.`code` IN ('DEVELOPER', 'RELEASE_APPROVER')
JOIN `role_permissions` rp ON rp.`role_id` = base_role.`id`
WHERE admin_role.`organization_id` IS NULL
  AND admin_role.`code` = 'DEVELOPER_ADMIN';

-- 需求审核与 UX 审核为领域管理员专属动作；本次只建码与授权，等需求状态机追加对应阶段后接入流转。
INSERT INTO `permissions` (`code`, `resource`, `action`, `description`) VALUES
  ('requirement.approve', 'requirement', 'approve', '由产品管理员执行需求审核，决定需求是否通过审核并进入下一阶段'),
  ('ux.approve', 'ux', 'approve', '由 UX 管理员执行 UX 审核，决定体验方案是否通过');

-- Owner 与全局管理员保持“全部权限”语义，需显式补授新权限码。
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` IN ('requirement.approve', 'ux.approve')
WHERE r.`organization_id` IS NULL AND r.`code` IN ('OWNER', 'ADMIN');

-- 需求审核只授予产品管理员。
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` = 'requirement.approve'
WHERE r.`organization_id` IS NULL AND r.`code` = 'PRODUCT_ADMIN';

-- UX 审核只授予 UX 管理员。
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` = 'ux.approve'
WHERE r.`organization_id` IS NULL AND r.`code` = 'UX_ADMIN';

-- 先清理关联关系再删除角色，避免外键约束失败；仅处理系统角色，不触碰公司自定义角色。
DELETE mr FROM `member_roles` mr
JOIN `roles` r ON r.`id` = mr.`role_id`
WHERE r.`code` = 'RELEASE_APPROVER' AND r.`organization_id` IS NULL;

DELETE rp FROM `role_permissions` rp
JOIN `roles` r ON r.`id` = rp.`role_id`
WHERE r.`code` = 'RELEASE_APPROVER' AND r.`organization_id` IS NULL;

DELETE FROM `roles` WHERE `code` = 'RELEASE_APPROVER' AND `organization_id` IS NULL;
