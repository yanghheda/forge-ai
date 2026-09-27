-- 发布写权限移交研发领域（ADR-025）：Release Candidate 的创建、确定性预检与模拟部署申请
-- 由交付物产出方（研发）承担；产品与测试只保留 release.read 查看发布状态与预检结论。

-- 授予研发基础角色与开发管理员三个发布写权限码。
-- 二者此前只有 release.read，本次新增映射不产生重复主键冲突。
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` IN ('release.manage', 'release.precheck', 'release.deploy')
WHERE r.`organization_id` IS NULL AND r.`code` IN ('DEVELOPER', 'DEVELOPER_ADMIN');

-- 收回产品、测试基础角色及其领域管理员的三个发布写权限码。
-- 领域管理员的权限是创建时点的快照，不会随基础角色变化自动增减，故需显式一并收回。
DELETE rp FROM `role_permissions` rp
JOIN `roles` r ON r.`id` = rp.`role_id`
JOIN `permissions` p ON p.`id` = rp.`permission_id`
WHERE r.`organization_id` IS NULL
  AND r.`code` IN ('PRODUCT', 'QA', 'PRODUCT_ADMIN', 'QA_ADMIN')
  AND p.`code` IN ('release.manage', 'release.precheck', 'release.deploy');
