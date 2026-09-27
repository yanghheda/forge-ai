-- 需求验收权限码：验收是产品领域成员对交付物是否满足验收标准的确认，
-- 与领域管理员专属的 requirement.approve 审核语义分离（ADR-024）。
INSERT INTO `permissions` (`code`, `resource`, `action`, `description`) VALUES
  ('requirement.accept', 'requirement', 'accept', '由产品领域成员执行需求验收，确认交付物满足验收标准并进入待发布');

-- 产品基础角色与产品管理员均可验收；Owner 与全局管理员保持“全部权限”语义，需显式补授新权限码。
-- 产品管理员只在 V7 时点继承产品基础角色当时的权限集合，不自动获得此后新增的权限码。
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `roles` r
JOIN `permissions` p ON p.`code` = 'requirement.accept'
WHERE r.`organization_id` IS NULL AND r.`code` IN ('PRODUCT', 'PRODUCT_ADMIN', 'ADMIN', 'OWNER');
