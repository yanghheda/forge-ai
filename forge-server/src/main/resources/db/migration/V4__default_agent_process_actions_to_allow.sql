ALTER TABLE `agent_runs`
  MODIFY COLUMN `medium_tool_confirmation` varchar(8) NOT NULL DEFAULT 'ALLOW'
  COMMENT '本轮 MEDIUM 风险 Tool 的执行策略：ALLOW 直接执行、ASK 兼容旧确认入口、DENY 禁止执行';
