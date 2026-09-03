-- 登录发生在选择 Workspace 之前；匿名失败也不存在可引用的用户主体。
ALTER TABLE audit_logs
    MODIFY COLUMN workspace_id BIGINT UNSIGNED NULL
        COMMENT '审计事件所属工作区；登录等实例级事件为空',
    MODIFY COLUMN actor_id BIGINT UNSIGNED NULL
        COMMENT '已识别操作者的用户标识；匿名登录失败为空';
