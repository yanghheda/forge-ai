ALTER TABLE work_items
    ADD KEY idx_work_items_scope_page (
        workspace_id,
        project_id,
        deleted_at,
        item_number DESC
    ) COMMENT '支持租户项目范围内未删除工作项的稳定倒序分页',
    ADD KEY idx_work_items_scope_type_status_page (
        workspace_id,
        project_id,
        type,
        status,
        deleted_at,
        item_number DESC
    ) COMMENT '支持租户项目范围内按类型状态筛选的稳定倒序分页';
