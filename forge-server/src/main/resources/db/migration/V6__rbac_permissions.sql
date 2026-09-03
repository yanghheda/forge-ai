CREATE TABLE permissions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '权限的稳定业务标识',
    code VARCHAR(100) NOT NULL COMMENT '资源与动作组成的稳定权限代码',
    resource VARCHAR(64) NOT NULL COMMENT '受保护业务资源的稳定分类',
    action VARCHAR(64) NOT NULL COMMENT '对资源执行的授权动作',
    description VARCHAR(500) NOT NULL COMMENT '权限覆盖的业务能力说明',
    PRIMARY KEY (id),
    UNIQUE KEY uq_permissions_code (code)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'RBAC 权限原子定义；默认拒绝时仅显式映射的权限有效';

CREATE TABLE role_permissions (
    role_id BIGINT UNSIGNED NOT NULL COMMENT '拥有该权限的角色标识',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '授予角色的原子权限标识',
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色与权限的多对多授权事实；没有映射即不允许';

INSERT INTO roles (workspace_id, code, name, system_role, description) VALUES
    (NULL, 'OWNER', 'Owner', TRUE, '工作区所有者；拥有全部默认权限但不绕过 HIGH 审批'),
    (NULL, 'ADMIN', 'Admin', TRUE, '工作区管理员；负责成员与项目管理'),
    (NULL, 'PRODUCT', 'Product', TRUE, '产品角色；负责需求与项目读取'),
    (NULL, 'UX', 'UX', TRUE, '体验设计角色；负责 UX 工作范围'),
    (NULL, 'DEVELOPER', 'Developer', TRUE, '研发角色；负责开发工作范围'),
    (NULL, 'QA', 'QA', TRUE, '质量角色；负责测试工作范围'),
    (NULL, 'RELEASE_APPROVER', 'Release Approver', TRUE, '发布审批角色；负责发布审核范围')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), system_role = TRUE;

INSERT INTO permissions (code, resource, action, description) VALUES
    ('workspace.read', 'workspace', 'read', '读取工作区可见信息'),
    ('workspace.manage', 'workspace', 'manage', '创建或管理工作区'),
    ('member.read', 'member', 'read', '读取成员关系'),
    ('member.manage', 'member', 'manage', '增删成员或分配角色'),
    ('project.read', 'project', 'read', '读取项目及其基础信息'),
    ('project.manage', 'project', 'manage', '创建或归档项目')
ON DUPLICATE KEY UPDATE resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code IN ('OWNER', 'ADMIN');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'workspace.read' WHERE r.code IN ('PRODUCT', 'UX', 'DEVELOPER', 'QA', 'RELEASE_APPROVER');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'project.read' WHERE r.code IN ('PRODUCT', 'UX', 'DEVELOPER', 'QA', 'RELEASE_APPROVER');
