-- 本轮只建立实例初始化所需的单行事实，不提前创建身份或其他领域表。
CREATE TABLE instance_settings (
    id TINYINT UNSIGNED NOT NULL COMMENT '固定为 1 的单例主键',
    initialized_at DATETIME(6) NULL COMMENT '首次管理员初始化完成时间；为空表示实例尚未初始化',
    default_organization_id BINARY(16) NULL COMMENT '初始化后创建的默认组织标识',
    settings_json JSON NOT NULL COMMENT '不属于独立领域表的实例级扩展设置',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '实例设置并发更新使用的乐观锁版本',
    PRIMARY KEY (id),
    CONSTRAINT chk_instance_settings_singleton CHECK (id = 1)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '实例级配置与初始化状态的唯一事实记录';

INSERT INTO instance_settings (id, initialized_at, default_organization_id, settings_json, version)
VALUES (1, NULL, NULL, JSON_OBJECT(), 0);
