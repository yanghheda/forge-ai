ALTER TABLE `agent_conversations`
  ADD COLUMN `requirement_id` bigint unsigned DEFAULT NULL COMMENT '会话绑定的 Requirement 标识；空值表示尚未选择或创建需求' AFTER `user_id`,
  ADD KEY `idx_agent_conversations_requirement` (`organization_id`,`requirement_id`),
  ADD CONSTRAINT `fk_agent_conversations_requirement` FOREIGN KEY (`requirement_id`) REFERENCES `work_items` (`id`);

UPDATE `agent_conversations` c
SET c.requirement_id = COALESCE(
  (
    SELECT ar.work_item_id
    FROM agent_messages m
    JOIN agent_runs ar ON ar.id = m.run_id
    JOIN work_items wi ON wi.id = ar.work_item_id
      AND wi.organization_id = c.organization_id
      AND wi.type = 'REQUIREMENT'
    WHERE m.conversation_id = c.id
      AND ar.organization_id = c.organization_id
      AND ar.user_id = c.user_id
    ORDER BY m.created_at DESC
    LIMIT 1
  ),
  (
    SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(tc.result_json,'$.id')) AS UNSIGNED)
    FROM agent_messages m
    JOIN agent_runs ar ON ar.id = m.run_id
    JOIN agent_tool_calls tc ON tc.run_id = ar.id
      AND tc.organization_id = c.organization_id
    JOIN work_items wi ON wi.id = CAST(JSON_UNQUOTE(JSON_EXTRACT(tc.result_json,'$.id')) AS UNSIGNED)
      AND wi.organization_id = c.organization_id
      AND wi.type = 'REQUIREMENT'
    WHERE m.conversation_id = c.id
      AND ar.organization_id = c.organization_id
      AND ar.user_id = c.user_id
      AND tc.tool_name = 'create_requirement'
      AND tc.status = 'SUCCEEDED'
    ORDER BY tc.created_at DESC
    LIMIT 1
  )
)
WHERE c.requirement_id IS NULL;
