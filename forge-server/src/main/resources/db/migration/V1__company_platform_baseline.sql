
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `agent_conversations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_conversations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Agent 会话的稳定业务标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '会话所属公司，用于服务端资源隔离',
  `user_id` bigint unsigned NOT NULL COMMENT '创建并拥有该会话的成员用户标识',
  `title` varchar(255) NOT NULL COMMENT '根据首条指令生成或由用户修改的会话标题',
  `created_at` datetime(6) NOT NULL COMMENT '会话创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '会话最近收到消息的 UTC 时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '会话并发更新使用的乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_agent_conversations_user_updated` (`organization_id`,`user_id`,`updated_at`),
  KEY `fk_agent_conversations_user` (`user_id`),
  CONSTRAINT `fk_agent_conversations_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_agent_conversations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成员与编排 Agent 进行连续交互的会话';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `agent_conversations` WRITE;
/*!40000 ALTER TABLE `agent_conversations` DISABLE KEYS */;
/*!40000 ALTER TABLE `agent_conversations` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `agent_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '持久事件数据库标识',
  `run_id` char(26) NOT NULL COMMENT '事件所属 Agent Run 标识',
  `sequence` bigint unsigned NOT NULL COMMENT 'Run 内严格递增且连续的业务事件序号',
  `event_type` varchar(64) NOT NULL COMMENT 'SSE event 字段使用的稳定事件类型',
  `request_id` varchar(100) NOT NULL COMMENT '触发该事件的请求关联标识',
  `payload_json` json NOT NULL COMMENT '仅包含 UI 恢复所需结构化字段的事件载荷',
  `created_at` datetime(6) NOT NULL COMMENT '事件与序号提交到 MySQL 的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_agent_events_run_sequence` (`run_id`,`sequence`),
  CONSTRAINT `fk_agent_events_run` FOREIGN KEY (`run_id`) REFERENCES `agent_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SSE 重放使用的追加写持久业务事件';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `agent_events` WRITE;
/*!40000 ALTER TABLE `agent_events` DISABLE KEYS */;
/*!40000 ALTER TABLE `agent_events` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `agent_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_messages` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Agent 会话消息的稳定业务标识',
  `conversation_id` bigint unsigned NOT NULL COMMENT '消息所属 Agent 会话标识',
  `sender` varchar(16) NOT NULL COMMENT '消息发送方；仅允许 USER 或 AGENT',
  `body` longtext NOT NULL COMMENT '用户可见消息正文，不保存模型隐式推理',
  `run_id` varchar(26) DEFAULT NULL COMMENT '该消息发起或产生的权威 Agent Run 标识；无执行时为空',
  `created_at` datetime(6) NOT NULL COMMENT '消息创建的 UTC 时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_messages_conversation_created` (`conversation_id`,`created_at`,`id`),
  KEY `fk_agent_messages_run` (`run_id`),
  CONSTRAINT `fk_agent_messages_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `agent_conversations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_agent_messages_run` FOREIGN KEY (`run_id`) REFERENCES `agent_runs` (`id`),
  CONSTRAINT `chk_agent_messages_sender` CHECK ((`sender` in (_utf8mb4'USER',_utf8mb4'AGENT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 连续会话中可展示的用户与 Agent 消息';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `agent_messages` WRITE;
/*!40000 ALTER TABLE `agent_messages` DISABLE KEYS */;
/*!40000 ALTER TABLE `agent_messages` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `agent_runs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_runs` (
  `id` char(26) NOT NULL COMMENT '服务端生成的 ULID Run 标识',
  `organization_id` bigint unsigned NOT NULL COMMENT 'Agent Run 所属公司标识',
  `work_item_id` bigint unsigned DEFAULT NULL COMMENT '可选上下文工作项；空值表示仅使用公司范围',
  `user_id` bigint unsigned NOT NULL COMMENT '发起 Run 的用户标识',
  `skill` varchar(32) NOT NULL COMMENT '本轮允许的 Product 或 UX Skill',
  `medium_tool_confirmation` varchar(8) NOT NULL DEFAULT 'ASK' COMMENT '本轮 MEDIUM 风险 Tool 的确认策略：ASK 需确认后执行、ALLOW 直接执行、DENY 拒绝执行',
  `message_redacted` varchar(500) NOT NULL COMMENT '不含原始正文的输入长度摘要',
  `client_request_id` varchar(100) NOT NULL COMMENT '客户端重试使用的幂等请求标识',
  `request_hash` char(64) NOT NULL COMMENT '识别幂等键是否被不同请求参数重用的 SHA-256 指纹',
  `status` varchar(32) NOT NULL COMMENT 'Backend 权威 Run 状态',
  `model_provider` varchar(64) DEFAULT NULL COMMENT '实际模型供应方；Fake Runner 阶段为空',
  `model_name` varchar(128) DEFAULT NULL COMMENT '实际模型名称；Fake Runner 阶段为空',
  `prompt_version` varchar(64) NOT NULL COMMENT '执行所使用的 Prompt 或 Fake Runner 版本',
  `started_at` datetime(6) DEFAULT NULL COMMENT '开始执行时间；排队期间为空',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '进入终态时间；未结束时为空',
  `token_input` bigint unsigned NOT NULL DEFAULT '0' COMMENT '输入 Token 计量；Fake Runner 固定为零',
  `token_output` bigint unsigned NOT NULL DEFAULT '0' COMMENT '输出 Token 计量；Fake Runner 固定为零',
  `cost` decimal(18,8) NOT NULL DEFAULT '0.00000000' COMMENT '本次运行成本；Fake Runner 固定为零',
  `error_code` varchar(100) DEFAULT NULL COMMENT '稳定失败码；成功或未结束时为空',
  `last_sequence` bigint unsigned NOT NULL DEFAULT '0' COMMENT '已提交的最后一个持久业务事件序号',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'Run 乐观演进版本',
  `created_at` datetime(6) NOT NULL COMMENT 'Run 创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT 'Run 最近状态更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_agent_runs_client_request` (`organization_id`,`user_id`,`client_request_id`),
  KEY `idx_agent_runs_user_status` (`user_id`,`status`),
  KEY `fk_agent_runs_work_item` (`work_item_id`),
  KEY `idx_agent_runs_organization_created` (`organization_id`,`created_at`),
  CONSTRAINT `fk_agent_runs_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_agent_runs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_agent_runs_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent Run 对外状态与事件序号的 Backend 权威事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `agent_runs` WRITE;
/*!40000 ALTER TABLE `agent_runs` DISABLE KEYS */;
/*!40000 ALTER TABLE `agent_runs` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `agent_steps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_steps` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Run 步骤数据库标识',
  `run_id` char(26) NOT NULL COMMENT '步骤所属 Agent Run 标识',
  `step_no` int unsigned NOT NULL COMMENT 'Run 内从一开始的稳定步骤序号',
  `type` varchar(32) NOT NULL COMMENT '步骤执行类型；本轮固定为 FAKE',
  `name` varchar(200) NOT NULL COMMENT '供时间线显示的简短步骤名称',
  `status` varchar(32) NOT NULL COMMENT '步骤当前执行状态',
  `input_summary` varchar(500) NOT NULL COMMENT '脱敏后的步骤输入摘要',
  `output_summary` varchar(500) DEFAULT NULL COMMENT '脱敏后的步骤输出摘要；未完成时为空',
  `error_code` varchar(100) DEFAULT NULL COMMENT '稳定步骤失败码；成功或未结束时为空',
  `started_at` datetime(6) NOT NULL COMMENT '步骤开始执行时间',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '步骤进入终态时间；执行中为空',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_agent_steps_run_step` (`run_id`,`step_no`),
  CONSTRAINT `fk_agent_steps_run` FOREIGN KEY (`run_id`) REFERENCES `agent_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent Run 可展示的脱敏步骤 Trace';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `agent_steps` WRITE;
/*!40000 ALTER TABLE `agent_steps` DISABLE KEYS */;
/*!40000 ALTER TABLE `agent_steps` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `agent_tool_calls`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_tool_calls` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Tool Call 数据库标识',
  `run_id` char(26) NOT NULL COMMENT 'Tool Call 所属 Agent Run 标识',
  `organization_id` bigint unsigned NOT NULL COMMENT 'Agent Tool 调用所属公司标识',
  `tool_call_id` varchar(64) NOT NULL COMMENT 'Agent 侧生成的本次调用标识，与 Run 组成幂等键',
  `tool_name` varchar(64) NOT NULL COMMENT '被执行的 Tool 契约名称',
  `tool_version` int unsigned NOT NULL COMMENT '被执行的 Tool 契约版本',
  `risk_level` varchar(8) NOT NULL COMMENT '契约声明的风险等级；本轮只持久化实际执行的 MEDIUM 写操作',
  `argument_hash` char(64) DEFAULT NULL COMMENT '规范化参数的 SHA-256 摘要，用于审批恢复时检测 TOCTOU',
  `status` varchar(32) NOT NULL COMMENT 'Tool Call 状态：WAITING_APPROVAL、SUCCEEDED、REJECTED、EXPIRED 或 CANCELLED',
  `arguments_json` json NOT NULL COMMENT '通过契约 Schema 校验的结构化输入参数',
  `result_json` json DEFAULT NULL COMMENT '成功执行后的结构化结果；审批等待或拒绝时为空',
  `idempotency_key` varchar(128) NOT NULL COMMENT 'runId 与 toolCallId 拼接的幂等键，成功副作用只发生一次',
  `approval_id` char(26) DEFAULT NULL COMMENT '需要人工审批时关联的审批标识；自动执行时为空',
  `created_at` datetime(6) NOT NULL COMMENT 'Tool Call 提交时间',
  `finished_at` datetime(6) DEFAULT NULL COMMENT 'Tool Call 完成时间；等待审批时为空',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_agent_tool_calls_run_call` (`run_id`,`tool_call_id`),
  UNIQUE KEY `uq_agent_tool_calls_idempotency` (`idempotency_key`),
  KEY `idx_agent_tool_calls_organization_created` (`organization_id`,`created_at`),
  CONSTRAINT `fk_agent_tool_calls_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_agent_tool_calls_run` FOREIGN KEY (`run_id`) REFERENCES `agent_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent Tool Call 的幂等事实与结构化结果；Backend 是 Tool 副作用的唯一权威';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `agent_tool_calls` WRITE;
/*!40000 ALTER TABLE `agent_tool_calls` DISABLE KEYS */;
/*!40000 ALTER TABLE `agent_tool_calls` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `approvals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approvals` (
  `id` char(26) NOT NULL COMMENT '服务端生成的审批 ULID 标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '审批所属公司标识',
  `run_id` char(26) NOT NULL COMMENT '触发审批的 Agent Run 标识',
  `tool_call_id` varchar(64) NOT NULL COMMENT '被冻结的原始 Tool Call 标识',
  `type` varchar(32) NOT NULL COMMENT '审批类型；本轮固定为 TOOL_EXECUTION',
  `risk_level` varchar(8) NOT NULL COMMENT 'Tool 契约声明的风险等级',
  `status` varchar(32) NOT NULL COMMENT 'PENDING、APPROVED、REJECTED、EXPIRED 或 CANCELLED',
  `requested_by` bigint unsigned NOT NULL COMMENT '请求执行 Tool 的 Run 发起用户',
  `approver_user_id` bigint unsigned DEFAULT NULL COMMENT '作出决定的用户；未决定时为空',
  `tool_name` varchar(64) NOT NULL COMMENT '冻结的 Tool 名称',
  `tool_version` int unsigned NOT NULL COMMENT '冻结的 Tool Contract 版本',
  `argument_hash` char(64) NOT NULL COMMENT '规范化参数 SHA-256 摘要',
  `frozen_arguments_encrypted` text NOT NULL COMMENT '使用服务端密钥 AES-GCM 加密的完整规范化参数',
  `resource_versions_json` json NOT NULL COMMENT '审批时读取的权威资源类型、标识与版本快照',
  `reason` varchar(500) NOT NULL COMMENT '向审批人解释风险与影响的脱敏原因',
  `expires_at` datetime(6) NOT NULL COMMENT '审批最晚可决策时间；过期后必须重新规划',
  `decided_at` datetime(6) DEFAULT NULL COMMENT '批准、拒绝、过期或取消的时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '决策接口使用的乐观锁版本',
  `created_at` datetime(6) NOT NULL COMMENT '审批创建 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_approvals_run_tool_call` (`run_id`,`tool_call_id`),
  KEY `fk_approvals_requester` (`requested_by`),
  KEY `fk_approvals_approver` (`approver_user_id`),
  KEY `idx_approvals_organization_status_expiry` (`organization_id`,`status`,`expires_at`),
  CONSTRAINT `fk_approvals_approver` FOREIGN KEY (`approver_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_approvals_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_approvals_requester` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_approvals_run` FOREIGN KEY (`run_id`) REFERENCES `agent_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent Tool 人工审批的冻结事实；浏览器会话不是审批事实来源';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `approvals` WRITE;
/*!40000 ALTER TABLE `approvals` DISABLE KEYS */;
/*!40000 ALTER TABLE `approvals` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '只追加审计记录的递增标识',
  `organization_id` bigint unsigned DEFAULT NULL COMMENT '审计事件所属公司；匿名登录等实例级事件为空',
  `actor_type` varchar(32) NOT NULL COMMENT '操作者类型；初始化事件使用 USER',
  `actor_id` bigint unsigned DEFAULT NULL COMMENT '已识别操作者的用户标识；匿名登录失败为空',
  `action` varchar(100) NOT NULL COMMENT '稳定的审计动作代码',
  `resource_type` varchar(64) NOT NULL COMMENT '被操作资源的稳定类型代码',
  `resource_id` bigint unsigned NOT NULL COMMENT '被操作资源的业务标识',
  `result` varchar(32) NOT NULL COMMENT '审计动作结果；成功初始化记录 SUCCESS',
  `request_id` varchar(80) NOT NULL COMMENT '贯穿 HTTP 响应与日志的请求标识',
  `run_id` varchar(80) DEFAULT NULL COMMENT '关联 Agent Run 的外部标识；人工初始化时为空',
  `metadata_redacted_json` json NOT NULL COMMENT '经过字段白名单脱敏的审计补充信息，禁止密码与摘要',
  `created_at` datetime(6) NOT NULL COMMENT '审计事实创建的 UTC 时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_logs_resource` (`resource_type`,`resource_id`),
  KEY `idx_audit_logs_organization_created` (`organization_id`,`created_at`),
  CONSTRAINT `fk_audit_logs_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关键业务与安全动作的应用级只追加审计记录';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `board_positions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `board_positions` (
  `work_item_id` bigint unsigned NOT NULL COMMENT '被排序工作项标识，每个工作项最多一个看板位置',
  `organization_id` bigint unsigned NOT NULL COMMENT '看板位置所属公司，用于服务端资源隔离',
  `lane` varchar(40) NOT NULL COMMENT '工作项当前泳道代码，由看板固定白名单约束',
  `position` bigint unsigned NOT NULL COMMENT '泳道内可留有间隔的正序位置',
  `updated_by` bigint unsigned NOT NULL COMMENT '最近调整位置的成员用户标识',
  `updated_at` datetime(6) NOT NULL COMMENT '最近调整位置的 UTC 时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '拖拽并发更新使用的乐观锁版本',
  PRIMARY KEY (`work_item_id`),
  KEY `idx_board_positions_lane` (`organization_id`,`lane`,`position`),
  KEY `fk_board_positions_updated_by` (`updated_by`),
  CONSTRAINT `fk_board_positions_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_board_positions_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_board_positions_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `chk_board_positions_lane` CHECK ((`lane` in (_utf8mb4'TODO',_utf8mb4'IN_PROGRESS',_utf8mb4'BLOCKED',_utf8mb4'DONE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司全局任务看板的泳道与稳定排序事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `board_positions` WRITE;
/*!40000 ALTER TABLE `board_positions` DISABLE KEYS */;
/*!40000 ALTER TABLE `board_positions` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `branches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `branches` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '本地分支快照稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '分支所属公司标识',
  `repository_id` bigint unsigned NOT NULL COMMENT '分支所属 Git 仓库绑定标识',
  `work_item_id` bigint unsigned DEFAULT NULL COMMENT '关联 Dev Task；仅同步未关联分支时为空',
  `name` varchar(255) NOT NULL COMMENT 'GitLab 仓库内的完整分支名',
  `commit_sha` varchar(64) NOT NULL COMMENT '最近一次从远端确认的分支头提交 SHA',
  `status` varchar(32) NOT NULL COMMENT '本地标准化缓存状态，远端事实优先',
  `remote_updated_at` datetime(6) NOT NULL COMMENT '远端资源更新时间；GitLab 未提供时为观察时间',
  `last_synced_at` datetime(6) NOT NULL COMMENT 'ForgeAI 最近完成远端核对的 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_branches_repository_name` (`repository_id`,`name`),
  KEY `fk_branches_work_item` (`work_item_id`),
  KEY `idx_branches_organization_work_item` (`organization_id`,`work_item_id`),
  CONSTRAINT `fk_branches_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_branches_repository` FOREIGN KEY (`repository_id`) REFERENCES `git_repositories` (`id`),
  CONSTRAINT `fk_branches_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='GitLab 分支的标准化本地缓存及 Dev Task 关联';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `branches` WRITE;
/*!40000 ALTER TABLE `branches` DISABLE KEYS */;
/*!40000 ALTER TABLE `branches` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `bug_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bug_details` (
  `work_item_id` bigint unsigned NOT NULL COMMENT 'BUG 工作项标识并作为详情主键',
  `organization_id` bigint unsigned NOT NULL COMMENT '缺陷详情所属公司标识',
  `requirement_id` bigint unsigned NOT NULL COMMENT '发现缺陷的 Requirement 标识',
  `test_run_id` bigint unsigned DEFAULT NULL COMMENT '发现缺陷的 Test Run；人工草稿可为空',
  `test_result_id` bigint unsigned DEFAULT NULL COMMENT 'FAIL 或 BLOCKED 的 Test Result；人工草稿可为空',
  `reproduction_steps_json` json NOT NULL COMMENT '按顺序保存的非空复现步骤',
  `expected_result` text NOT NULL COMMENT '测试期望结果',
  `actual_result` text NOT NULL COMMENT '触发缺陷的实际结果',
  `fix_note` text COMMENT '研发解决时填写的修复说明；未解决时为空',
  `fix_evidence_json` json DEFAULT NULL COMMENT '关联 MR、Commit 等修复证据；未解决时为空',
  `verified_by` bigint unsigned DEFAULT NULL COMMENT '执行独立验证的 QA 或管理员；未验证时为空',
  `verified_at` datetime(6) DEFAULT NULL COMMENT '最近一次验证 UTC 时间；未验证或 reopen 后为空',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '详情并发修改使用的乐观锁版本',
  PRIMARY KEY (`work_item_id`),
  KEY `idx_bug_details_requirement` (`requirement_id`),
  KEY `idx_bug_details_result` (`test_result_id`),
  KEY `fk_bug_details_run` (`test_run_id`),
  KEY `fk_bug_details_verifier` (`verified_by`),
  KEY `fk_bug_details_organization` (`organization_id`),
  CONSTRAINT `fk_bug_details_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `fk_bug_details_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_bug_details_requirement` FOREIGN KEY (`requirement_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `fk_bug_details_result` FOREIGN KEY (`test_result_id`) REFERENCES `test_results` (`id`),
  CONSTRAINT `fk_bug_details_run` FOREIGN KEY (`test_run_id`) REFERENCES `test_runs` (`id`),
  CONSTRAINT `fk_bug_details_verifier` FOREIGN KEY (`verified_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bug 的复现、测试来源、修复证据和独立验证事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `bug_details` WRITE;
/*!40000 ALTER TABLE `bug_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `bug_details` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '评论的稳定业务标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '评论所属公司标识',
  `work_item_id` bigint unsigned NOT NULL COMMENT '评论归属的工作项标识',
  `author_user_id` bigint unsigned NOT NULL COMMENT '评论作者用户标识',
  `body` varchar(4000) NOT NULL COMMENT '评论正文；去除首尾空白后不能为空',
  `created_at` datetime(6) NOT NULL COMMENT '评论创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '评论最近编辑的 UTC 时间；本轮仅创建因此等于创建时间',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '逻辑删除时间；为空表示可在 Activity 中展示',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '评论乐观锁版本；本轮创建初值为零',
  PRIMARY KEY (`id`),
  KEY `idx_comments_timeline` (`work_item_id`,`created_at`,`id`),
  KEY `idx_comments_scope` (`organization_id`,`work_item_id`),
  KEY `fk_comments_author` (`author_user_id`),
  CONSTRAINT `fk_comments_author` FOREIGN KEY (`author_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_comments_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_comments_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Work Item 用户讨论事实；统一 Activity 只投影未删除评论';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `comments` WRITE;
/*!40000 ALTER TABLE `comments` DISABLE KEYS */;
/*!40000 ALTER TABLE `comments` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `deployments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deployments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '部署记录递增标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '部署所属公司标识',
  `release_id` bigint unsigned NOT NULL COMMENT '被部署的 Release Candidate 标识',
  `mode` varchar(16) NOT NULL COMMENT '部署模式；MVP 仅允许 SIMULATED',
  `status` varchar(32) NOT NULL COMMENT '部署状态：PENDING_APPROVAL、APPROVED、DEPLOYING、SUCCEEDED、FAILED、REJECTED 或 EXPIRED',
  `requested_by` bigint unsigned NOT NULL COMMENT '发起部署请求的用户标识',
  `approver_user_id` bigint unsigned DEFAULT NULL COMMENT '审批用户标识；尚未决策时为空',
  `approval_expires_at` datetime(6) NOT NULL COMMENT '人工审批失效的 UTC 时间',
  `decided_at` datetime(6) DEFAULT NULL COMMENT '审批决定或过期的 UTC 时间；未决策时为空',
  `release_version` bigint unsigned NOT NULL COMMENT '申请时冻结的 Release 乐观锁版本',
  `precheck_id` bigint unsigned NOT NULL COMMENT '申请时冻结的 PASS Precheck 快照标识',
  `argument_hash` char(64) NOT NULL COMMENT '规范化部署参数的 SHA-256 摘要',
  `simulate_failure` tinyint(1) NOT NULL COMMENT '测试专用确定性失败开关；不代表真实环境结果',
  `idempotency_key` varchar(128) NOT NULL COMMENT '调用方提供的部署请求幂等键',
  `result_code` varchar(64) DEFAULT NULL COMMENT '模拟器稳定结果码；执行前为空',
  `result_summary` varchar(500) DEFAULT NULL COMMENT '明确声明模拟性质的结果摘要；执行前为空',
  `approval_source` varchar(16) NOT NULL COMMENT '审批来源：HUMAN_REQUEST 或 AGENT_TOOL',
  `agent_approval_id` char(26) DEFAULT NULL COMMENT 'Agent HIGH Tool 已批准事实标识；人工入口为空',
  `created_at` datetime(6) NOT NULL COMMENT '部署请求创建 UTC 时间',
  `started_at` datetime(6) DEFAULT NULL COMMENT 'worker 开始模拟执行的 UTC 时间；未执行时为空',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '模拟执行结束的 UTC 时间；未结束时为空',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '审批与 worker 抢占使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_deployments_organization_idempotency` (`organization_id`,`idempotency_key`),
  KEY `idx_deployments_status_created` (`status`,`created_at`),
  KEY `fk_deployments_release` (`release_id`),
  KEY `fk_deployments_requester` (`requested_by`),
  KEY `fk_deployments_approver` (`approver_user_id`),
  KEY `fk_deployments_precheck` (`precheck_id`),
  KEY `fk_deployments_agent_approval` (`agent_approval_id`),
  KEY `idx_deployments_organization_release` (`organization_id`,`release_id`),
  CONSTRAINT `fk_deployments_agent_approval` FOREIGN KEY (`agent_approval_id`) REFERENCES `approvals` (`id`),
  CONSTRAINT `fk_deployments_approver` FOREIGN KEY (`approver_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_deployments_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_deployments_precheck` FOREIGN KEY (`precheck_id`) REFERENCES `release_prechecks` (`id`),
  CONSTRAINT `fk_deployments_release` FOREIGN KEY (`release_id`) REFERENCES `releases` (`id`),
  CONSTRAINT `fk_deployments_requester` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HIGH 审批保护且明确标记为模拟执行的部署记录';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `deployments` WRITE;
/*!40000 ALTER TABLE `deployments` DISABLE KEYS */;
/*!40000 ALTER TABLE `deployments` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `document_index_jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_index_jobs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '文档索引任务稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '索引任务所属公司标识',
  `document_id` bigint unsigned NOT NULL COMMENT '待索引的文档标识',
  `version_id` bigint unsigned NOT NULL COMMENT '待索引的不可变文档版本标识',
  `status` varchar(24) NOT NULL COMMENT '任务状态：PENDING 待处理、INDEXING 已领取、SUCCEEDED 已完成、FAILED 待重试、DEAD 超过重试上限待人工处理',
  `attempts` int unsigned NOT NULL DEFAULT '0' COMMENT '已失败尝试次数；达到上限后任务转 DEAD',
  `next_attempt_at` datetime(6) NOT NULL COMMENT '下次允许领取的 UTC 时间；FAILED 退避与 INDEXING 租约共用',
  `chunk_count` int unsigned DEFAULT NULL COMMENT '成功索引的切片数量；未成功时为空',
  `error_code` varchar(64) DEFAULT NULL COMMENT '最近一次失败的稳定错误代码；成功时为空',
  `error_message` varchar(512) DEFAULT NULL COMMENT '最近一次失败的截断描述；不含文档正文',
  `indexed_at` datetime(6) DEFAULT NULL COMMENT '索引成功完成的 UTC 时间；未成功时为空',
  `created_at` datetime(6) NOT NULL COMMENT '任务创建 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '任务状态最近变更 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_document_index_jobs_version` (`document_id`,`version_id`),
  KEY `idx_document_index_jobs_claim` (`status`,`next_attempt_at`),
  KEY `fk_document_index_jobs_version` (`version_id`),
  KEY `fk_document_index_jobs_organization` (`organization_id`),
  CONSTRAINT `fk_document_index_jobs_document` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`),
  CONSTRAINT `fk_document_index_jobs_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_document_index_jobs_version` FOREIGN KEY (`version_id`) REFERENCES `document_versions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Outbox 事件派生的文档索引重试队列；MySQL 是事实来源，Qdrant 仅是可重建的派生索引';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `document_index_jobs` WRITE;
/*!40000 ALTER TABLE `document_index_jobs` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_index_jobs` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `document_versions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_versions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '不可变文档版本稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '文档版本所属公司标识',
  `document_id` bigint unsigned NOT NULL COMMENT '所属文档元数据标识',
  `version_no` bigint unsigned NOT NULL COMMENT '文档内单调递增版本号；从 1 开始',
  `content_format` varchar(32) NOT NULL COMMENT '正文格式；本轮固定为 PROSEMIRROR_JSON',
  `content` json NOT NULL COMMENT 'Tiptap ProseMirror JSON 正文；版本创建后禁止修改',
  `content_hash` char(64) NOT NULL COMMENT '规范化纯文本的 SHA-256，用于内容标识与检索去重',
  `plain_text` longtext NOT NULL COMMENT '由服务端规范化生成的搜索与摘要文本，不信任客户端提供',
  `summary` text COMMENT '可选摘要；本轮创建时为空',
  `created_by` bigint unsigned NOT NULL COMMENT '创建本版本的用户标识',
  `created_at` datetime(6) NOT NULL COMMENT '版本创建 UTC 时间；版本创建后不可更改',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_document_versions_number` (`document_id`,`version_no`),
  KEY `fk_document_versions_creator` (`created_by`),
  KEY `idx_document_versions_history` (`organization_id`,`document_id`,`version_no` DESC),
  CONSTRAINT `fk_document_versions_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_document_versions_document` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`),
  CONSTRAINT `fk_document_versions_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档正文的追加写不可变版本；历史记录不允许普通更新或删除';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `document_versions` WRITE;
/*!40000 ALTER TABLE `document_versions` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_versions` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `documents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `documents` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '文档元数据稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '文档所属公司标识',
  `work_item_id` bigint unsigned DEFAULT NULL COMMENT '可选关联工作项；本会话允许为空',
  `type` varchar(40) NOT NULL COMMENT '文档业务类型；本轮支持 PRD',
  `title` varchar(255) NOT NULL COMMENT '文档显示标题；不包含版本号',
  `status` varchar(24) NOT NULL COMMENT '文档生命周期状态：DRAFT、PUBLISHED 或 ARCHIVED',
  `visibility` varchar(24) NOT NULL COMMENT '文档可见性；固定为公司范围 ORGANIZATION',
  `current_version_id` bigint unsigned DEFAULT NULL COMMENT '当前读取和发布引用的不可变版本；草稿可为空',
  `created_by` bigint unsigned NOT NULL COMMENT '创建文档元数据的用户标识',
  `created_at` datetime(6) NOT NULL COMMENT '文档元数据创建 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '文档元数据或当前版本最近更新 UTC 时间',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '逻辑删除 UTC 时间；非空文档对普通查询不可见',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '文档元数据和保存操作使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_documents_work_item_type` (`work_item_id`,`type`),
  KEY `fk_documents_creator` (`created_by`),
  KEY `fk_documents_current_version` (`current_version_id`),
  KEY `idx_documents_organization_type_status` (`organization_id`,`type`,`status`),
  CONSTRAINT `fk_documents_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_documents_current_version` FOREIGN KEY (`current_version_id`) REFERENCES `document_versions` (`id`),
  CONSTRAINT `fk_documents_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_documents_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档元数据与当前版本指针；正文仅存于不可变版本表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `documents` WRITE;
/*!40000 ALTER TABLE `documents` DISABLE KEYS */;
/*!40000 ALTER TABLE `documents` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `git_repositories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `git_repositories` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '绑定仓库的本地稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT 'GitLab 仓库所属公司标识',
  `connection_id` bigint unsigned NOT NULL COMMENT '读取该远端仓库所使用的 GitLab 连接标识',
  `remote_project_id` varchar(128) NOT NULL COMMENT 'GitLab 返回的不可变项目标识',
  `path_with_namespace` varchar(500) NOT NULL COMMENT 'GitLab 仓库的命名空间完整路径',
  `http_url` varchar(1000) NOT NULL COMMENT 'GitLab 返回的仓库浏览或克隆 HTTPS 地址，不含凭据',
  `default_branch` varchar(255) DEFAULT NULL COMMENT '远端默认分支；GitLab 未设置时为空',
  `status` varchar(32) NOT NULL COMMENT '绑定状态，MVP 使用 ACTIVE',
  `last_synced_at` datetime(6) NOT NULL COMMENT '最近一次从 GitLab 读取仓库事实的 UTC 时间',
  `created_at` datetime(6) NOT NULL COMMENT '仓库绑定创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '仓库远端快照最近更新的 UTC 时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '仓库绑定更新使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_git_repositories_connection_remote` (`connection_id`,`remote_project_id`),
  KEY `idx_git_repositories_organization_status` (`organization_id`,`status`),
  CONSTRAINT `fk_git_repositories_connection` FOREIGN KEY (`connection_id`) REFERENCES `gitlab_connections` (`id`),
  CONSTRAINT `fk_git_repositories_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司与 GitLab 仓库的绑定及只读远端快照';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `git_repositories` WRITE;
/*!40000 ALTER TABLE `git_repositories` DISABLE KEYS */;
/*!40000 ALTER TABLE `git_repositories` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `gitlab_connections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gitlab_connections` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'GitLab 连接的本地稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT 'GitLab 连接所属公司标识',
  `name` varchar(120) NOT NULL COMMENT '公司内供管理员辨识连接的名称',
  `base_url` varchar(500) NOT NULL COMMENT '已规范化并通过 SSRF 策略校验的 GitLab HTTPS 根地址',
  `credential_secret_id` bigint unsigned NOT NULL COMMENT '当前 GitLab Token 对应的加密凭据标识',
  `webhook_secret_id` bigint unsigned DEFAULT NULL COMMENT '后续 Webhook 会话使用的加密 Secret，本轮保持为空',
  `status` varchar(32) NOT NULL COMMENT '连接状态：UNVERIFIED、ACTIVE 或 ERROR',
  `last_tested_at` datetime(6) DEFAULT NULL COMMENT '最近一次远端连接测试完成的 UTC 时间',
  `created_by` bigint unsigned NOT NULL COMMENT '创建该连接的公司管理员用户标识',
  `created_at` datetime(6) NOT NULL COMMENT '连接创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '连接配置或测试状态最近更新的 UTC 时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '配置更新与 Token 轮换使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_gitlab_connections_organization_name` (`organization_id`,`name`),
  KEY `fk_gitlab_connections_secret` (`credential_secret_id`),
  KEY `fk_gitlab_connections_webhook_secret` (`webhook_secret_id`),
  KEY `fk_gitlab_connections_creator` (`created_by`),
  CONSTRAINT `fk_gitlab_connections_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_gitlab_connections_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_gitlab_connections_secret` FOREIGN KEY (`credential_secret_id`) REFERENCES `secrets` (`id`),
  CONSTRAINT `fk_gitlab_connections_webhook_secret` FOREIGN KEY (`webhook_secret_id`) REFERENCES `secrets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司管理员配置的 GitLab API 连接，不包含明文 Token';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `gitlab_connections` WRITE;
/*!40000 ALTER TABLE `gitlab_connections` DISABLE KEYS */;
/*!40000 ALTER TABLE `gitlab_connections` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `instance_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `instance_settings` (
  `id` tinyint unsigned NOT NULL COMMENT '固定为 1 的单例主键',
  `initialized_at` datetime(6) DEFAULT NULL COMMENT '首次管理员初始化完成时间；为空表示实例尚未初始化',
  `default_organization_id` bigint unsigned DEFAULT NULL COMMENT '初始化后创建的默认组织标识；为空表示实例尚未完成初始化',
  `settings_json` json NOT NULL COMMENT '不属于独立领域表的实例级扩展设置',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '实例设置并发更新使用的乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `fk_instance_settings_default_organization` (`default_organization_id`),
  CONSTRAINT `fk_instance_settings_default_organization` FOREIGN KEY (`default_organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `chk_instance_settings_singleton` CHECK ((`id` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实例级配置与初始化状态的唯一事实记录';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `instance_settings` WRITE;
/*!40000 ALTER TABLE `instance_settings` DISABLE KEYS */;
INSERT INTO `instance_settings` VALUES (1,NULL,NULL,'{}',0);
/*!40000 ALTER TABLE `instance_settings` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `member_roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_roles` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '成员角色分配的稳定业务标识',
  `organization_member_id` bigint unsigned NOT NULL COMMENT '获得角色的公司成员关系标识',
  `role_id` bigint unsigned NOT NULL COMMENT '分配给成员的角色标识',
  `created_at` datetime(6) NOT NULL COMMENT '角色分配创建的 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_member_roles_company` (`organization_member_id`,`role_id`),
  KEY `fk_member_roles_role` (`role_id`),
  CONSTRAINT `fk_member_roles_company_member` FOREIGN KEY (`organization_member_id`) REFERENCES `organization_members` (`id`),
  CONSTRAINT `fk_member_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司成员的角色分配';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `member_roles` WRITE;
/*!40000 ALTER TABLE `member_roles` DISABLE KEYS */;
/*!40000 ALTER TABLE `member_roles` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `merge_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merge_requests` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '本地 MR 快照稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT 'Merge Request 所属公司标识',
  `repository_id` bigint unsigned NOT NULL COMMENT 'MR 所属 Git 仓库绑定标识',
  `work_item_id` bigint unsigned DEFAULT NULL COMMENT '关联 Dev Task；未关联同步记录时为空',
  `remote_mr_iid` bigint unsigned NOT NULL COMMENT 'GitLab 在项目内分配的 MR IID',
  `title` varchar(255) NOT NULL COMMENT '最近一次同步的 MR 标题',
  `source_branch` varchar(255) NOT NULL COMMENT 'MR 源分支',
  `target_branch` varchar(255) NOT NULL COMMENT 'MR 目标分支',
  `state` varchar(32) NOT NULL COMMENT 'GitLab 返回的标准化 MR 状态快照',
  `web_url` varchar(1000) NOT NULL COMMENT '不含凭据的 GitLab MR 页面地址',
  `author_external_id` varchar(128) DEFAULT NULL COMMENT 'GitLab 作者外部标识；无法获得时为空',
  `head_sha` varchar(64) DEFAULT NULL COMMENT '最近同步的源分支头 SHA；远端未返回时为空',
  `merge_status` varchar(64) DEFAULT NULL COMMENT 'GitLab 合并检查状态；远端未计算时为空',
  `remote_updated_at` datetime(6) NOT NULL COMMENT 'GitLab 返回的 MR 更新时间',
  `last_synced_at` datetime(6) NOT NULL COMMENT 'ForgeAI 最近完成远端核对的 UTC 时间',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '本地关联更新使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_merge_requests_repository_iid` (`repository_id`,`remote_mr_iid`),
  KEY `fk_merge_requests_work_item` (`work_item_id`),
  KEY `idx_merge_requests_organization_work_item` (`organization_id`,`work_item_id`),
  CONSTRAINT `fk_merge_requests_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_merge_requests_repository` FOREIGN KEY (`repository_id`) REFERENCES `git_repositories` (`id`),
  CONSTRAINT `fk_merge_requests_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='GitLab Merge Request 标准化缓存及 Dev Task 关联';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `merge_requests` WRITE;
/*!40000 ALTER TABLE `merge_requests` DISABLE KEYS */;
/*!40000 ALTER TABLE `merge_requests` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '通知的稳定业务标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '通知所属公司，用于服务端资源隔离',
  `user_id` bigint unsigned NOT NULL COMMENT '接收通知的公司成员用户标识',
  `type` varchar(64) NOT NULL COMMENT '通知类型，例如审批、交付状态或系统提醒',
  `title` varchar(255) NOT NULL COMMENT '通知列表展示的简短标题',
  `body` varchar(2000) NOT NULL COMMENT '通知正文摘要，空字符串表示无需补充说明',
  `resource_type` varchar(64) DEFAULT NULL COMMENT '可选关联资源类型；为空表示普通系统通知',
  `resource_id` varchar(80) DEFAULT NULL COMMENT '可选关联资源标识；为空表示没有跳转目标',
  `read_at` datetime(6) DEFAULT NULL COMMENT '用户首次标记已读的 UTC 时间；为空表示未读',
  `created_at` datetime(6) NOT NULL COMMENT '通知生成的 UTC 时间',
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_unread` (`organization_id`,`user_id`,`read_at`,`created_at`),
  KEY `fk_notifications_user` (`user_id`),
  CONSTRAINT `fk_notifications_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司成员的站内通知与已读事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
/*!40000 ALTER TABLE `notifications` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `organization_item_sequences`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization_item_sequences` (
  `organization_id` bigint unsigned NOT NULL COMMENT '编号序列所属公司标识',
  `next_value` bigint unsigned NOT NULL DEFAULT '1' COMMENT '下一次成功分配使用的公司级正整数',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '编号序列行锁写入的版本标识',
  PRIMARY KEY (`organization_id`),
  CONSTRAINT `fk_organization_item_sequences_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司内所有 Work Item 共享的原子编号序列';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `organization_item_sequences` WRITE;
/*!40000 ALTER TABLE `organization_item_sequences` DISABLE KEYS */;
/*!40000 ALTER TABLE `organization_item_sequences` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `organization_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization_members` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '公司成员关系的稳定业务标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '成员所属公司标识',
  `user_id` bigint unsigned NOT NULL COMMENT '加入公司的实例用户标识',
  `status` varchar(32) NOT NULL COMMENT '成员状态；PENDING、ACTIVE 或 DISABLED',
  `joined_at` datetime(6) DEFAULT NULL COMMENT '成员正式启用的 UTC 时间；待审核时为空',
  `created_at` datetime(6) NOT NULL COMMENT '成员关系创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '成员关系最近更新的 UTC 时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '成员并发更新使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_organization_members_user` (`organization_id`,`user_id`),
  KEY `idx_organization_members_status` (`organization_id`,`status`,`user_id`),
  KEY `fk_organization_members_user` (`user_id`),
  CONSTRAINT `fk_organization_members_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_organization_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户与实例唯一公司的成员及审核状态事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `organization_members` WRITE;
/*!40000 ALTER TABLE `organization_members` DISABLE KEYS */;
/*!40000 ALTER TABLE `organization_members` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `organization_policies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organization_policies` (
  `organization_id` bigint unsigned NOT NULL COMMENT '策略所属公司标识，每个公司恰好一条',
  `allow_skip_ux` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否允许需求跳过 UX 阶段',
  `ci_required` tinyint(1) NOT NULL DEFAULT '1' COMMENT '提交 QA 前是否强制每个 Dev Task 的 MR 当前 head Pipeline 成功；无仓库时不放行',
  `updated_by` bigint unsigned NOT NULL COMMENT '最近更新策略的用户标识',
  `updated_at` datetime(6) NOT NULL COMMENT '最近更新策略的 UTC 时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '策略并发更新使用的乐观锁版本',
  PRIMARY KEY (`organization_id`),
  KEY `fk_organization_policies_updated_by` (`updated_by`),
  CONSTRAINT `fk_organization_policies_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_organization_policies_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司级交付流程策略';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `organization_policies` WRITE;
/*!40000 ALTER TABLE `organization_policies` DISABLE KEYS */;
/*!40000 ALTER TABLE `organization_policies` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `organizations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `organizations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '组织的稳定业务标识',
  `name` varchar(120) NOT NULL COMMENT '组织的界面展示名称',
  `slug` varchar(80) NOT NULL COMMENT '组织在实例内唯一且可用于路由的规范化短名',
  `owner_user_id` bigint unsigned NOT NULL COMMENT '组织所有者用户标识；初始化时指向首个用户',
  `created_at` datetime(6) NOT NULL COMMENT '组织创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '组织最近一次更新的 UTC 时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '组织并发更新使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_organizations_slug` (`slug`),
  KEY `fk_organizations_owner` (`owner_user_id`),
  CONSTRAINT `fk_organizations_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='当前部署实例对应的公司事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `organizations` WRITE;
/*!40000 ALTER TABLE `organizations` DISABLE KEYS */;
/*!40000 ALTER TABLE `organizations` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `outbox_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbox_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Outbox 追加事件稳定标识',
  `organization_id` bigint unsigned DEFAULT NULL COMMENT '事件所属公司；实例级事件为空',
  `aggregate_type` varchar(64) NOT NULL COMMENT '产生事件的聚合类型；本轮为 DOCUMENT',
  `aggregate_id` bigint unsigned NOT NULL COMMENT '产生事件的文档聚合标识',
  `event_type` varchar(64) NOT NULL COMMENT '稳定事件类型；本轮为 DOCUMENT_VERSION_PUBLISHED',
  `payload` json NOT NULL COMMENT '下游索引所需的冻结版本引用与范围数据',
  `created_at` datetime(6) NOT NULL COMMENT '业务事务内写入的 UTC 时间',
  `processed_at` datetime(6) DEFAULT NULL COMMENT '异步消费者完成处理的 UTC 时间；本轮不消费',
  PRIMARY KEY (`id`),
  KEY `idx_outbox_events_pending` (`processed_at`,`id`),
  KEY `fk_outbox_events_organization` (`organization_id`),
  CONSTRAINT `fk_outbox_events_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务事务与异步索引之间的可靠事件交接；本轮只生产不消费';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `outbox_events` WRITE;
/*!40000 ALTER TABLE `outbox_events` DISABLE KEYS */;
/*!40000 ALTER TABLE `outbox_events` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '权限的稳定业务标识',
  `code` varchar(100) NOT NULL COMMENT '资源与动作组成的稳定权限代码',
  `resource` varchar(64) NOT NULL COMMENT '受保护业务资源的稳定分类',
  `action` varchar(64) NOT NULL COMMENT '对资源执行的授权动作',
  `description` varchar(500) NOT NULL COMMENT '权限覆盖的业务能力说明',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_permissions_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RBAC 权限原子定义；默认拒绝时仅显式映射的权限有效';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `permissions` WRITE;
/*!40000 ALTER TABLE `permissions` DISABLE KEYS */;
INSERT INTO `permissions` VALUES (3,'member.read','member','read','读取成员关系'),(4,'member.manage','member','manage','增删成员或分配角色'),(7,'requirement.read','requirement','read','读取公司范围内的 Requirement'),(8,'requirement.create','requirement','create','在公司范围内创建 Requirement'),(9,'requirement.edit','requirement','edit','修改 Requirement 的基础字段'),(10,'ux.read','ux','read','读取公司范围内的 UX Task'),(11,'ux.create','ux','create','在公司范围内创建 UX Task'),(12,'ux.edit','ux','edit','修改 UX Task 的基础字段'),(13,'task.read','task','read','读取公司范围内的 Dev 或 QA Task'),(14,'task.create','task','create','在公司范围内创建 Dev 或 QA Task'),(15,'task.edit','task','edit','修改 Dev 或 QA Task 的基础字段'),(16,'requirement.review','requirement','review','执行 Requirement 产品评审动作'),(17,'document.read','document','read','读取公司范围内的文档与历史版本'),(18,'document.create','document','create','在公司范围内创建文档'),(19,'document.edit','document','edit','保存新的不可变文档版本'),(20,'document.publish','document','publish','发布指定文档版本'),(21,'ux.review','ux','review','批准或退回 UX Task 与 UX 阶段评审'),(22,'ux.skip','ux','skip','在公司策略与分类允许时跳过 Requirement UX 阶段'),(23,'agent.run','agent','run','在授权公司范围内创建并读取 Agent Run'),(24,'approval.decide','approval','decide','批准或拒绝公司范围内他人发起的 Agent Tool 审批'),(25,'integration.manage','integration','manage','配置、轮换和测试公司外部系统连接'),(26,'repo.read','repo','read','读取并绑定公司范围内的源代码仓库'),(27,'repo.write','repo','write','触发公司绑定仓库的 Pipeline 等受控远端写操作'),(28,'development.submit','development','submit','在确定性研发与 CI Guard 通过后提交 Requirement 到 QA'),(29,'qa.manage','qa','manage','创建公司范围内的测试用例与测试运行'),(30,'qa.execute','qa','execute','执行测试、完成或重新打开测试运行'),(31,'qa.read','qa','read','读取公司范围内的测试用例与运行汇总'),(32,'bug.read','bug','read','读取公司范围内的 Bug'),(33,'bug.create','bug','create','基于失败测试或人工发现创建 Bug 草稿'),(34,'bug.edit','bug','edit','编辑或取消公司范围内的 Bug'),(35,'bug.resolve','bug','resolve','开始修复并提交带 MR 或 Commit 的修复证据'),(36,'bug.verify','bug','verify','由 QA 或管理员独立验证、关闭或重开 Bug'),(37,'release.read','release','read','读取公司范围内的 Release Candidate 与 Precheck'),(38,'release.manage','release','manage','创建或编辑 Release Candidate 与 Release Note'),(39,'release.precheck','release','precheck','执行确定性发布前检查并保存不可变快照'),(40,'release.deploy','release','deploy','申请或执行经过 HIGH 审批的模拟部署');
/*!40000 ALTER TABLE `permissions` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `pipeline_runs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pipeline_runs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '本地 Pipeline 快照稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT 'Pipeline 所属公司标识',
  `repository_id` bigint unsigned NOT NULL COMMENT 'Pipeline 所属 Git 仓库绑定标识',
  `merge_request_id` bigint unsigned DEFAULT NULL COMMENT '可选关联 MR；分支 Pipeline 或尚未匹配时为空',
  `remote_pipeline_id` bigint unsigned NOT NULL COMMENT 'GitLab 在项目内分配的 Pipeline 标识',
  `ref` varchar(255) NOT NULL COMMENT 'Pipeline 执行所针对的分支或标签',
  `commit_sha` varchar(64) NOT NULL COMMENT 'Pipeline 执行所针对的提交 SHA',
  `status` varchar(32) NOT NULL COMMENT 'GitLab 返回的 Pipeline 执行状态',
  `web_url` varchar(1000) NOT NULL COMMENT '不含凭据的 GitLab Pipeline 页面地址',
  `started_at` datetime(6) DEFAULT NULL COMMENT 'GitLab 记录的开始时间；尚未开始时为空',
  `finished_at` datetime(6) DEFAULT NULL COMMENT 'GitLab 记录的结束时间；未结束时为空',
  `remote_updated_at` datetime(6) NOT NULL COMMENT '用于拒绝乱序覆盖的 GitLab 对象更新时间',
  `last_synced_at` datetime(6) NOT NULL COMMENT 'ForgeAI 最近同步该快照的 UTC 时间',
  `summary_json` json NOT NULL COMMENT 'UI 展示所需的有界标准化摘要，不含完整 Job 日志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_pipeline_runs_repository_remote` (`repository_id`,`remote_pipeline_id`),
  KEY `fk_pipeline_runs_merge_request` (`merge_request_id`),
  KEY `idx_pipeline_runs_organization_repository` (`organization_id`,`repository_id`,`id`),
  CONSTRAINT `fk_pipeline_runs_merge_request` FOREIGN KEY (`merge_request_id`) REFERENCES `merge_requests` (`id`),
  CONSTRAINT `fk_pipeline_runs_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_pipeline_runs_repository` FOREIGN KEY (`repository_id`) REFERENCES `git_repositories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='GitLab Pipeline 的标准化最终一致本地快照';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `pipeline_runs` WRITE;
/*!40000 ALTER TABLE `pipeline_runs` DISABLE KEYS */;
/*!40000 ALTER TABLE `pipeline_runs` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `release_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `release_items` (
  `release_id` bigint unsigned NOT NULL COMMENT 'Release Candidate 标识',
  `work_item_id` bigint unsigned NOT NULL COMMENT '纳入本次发布的 Requirement 标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '发布项所属公司标识',
  PRIMARY KEY (`release_id`,`work_item_id`),
  KEY `idx_release_items_scope` (`organization_id`),
  KEY `fk_release_items_item` (`work_item_id`),
  CONSTRAINT `fk_release_items_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `fk_release_items_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_release_items_release` FOREIGN KEY (`release_id`) REFERENCES `releases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Release Candidate 纳入的 Requirement 集合';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `release_items` WRITE;
/*!40000 ALTER TABLE `release_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `release_items` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `release_prechecks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `release_prechecks` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '一次不可变 Precheck 快照标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '发布预检所属公司标识',
  `release_id` bigint unsigned NOT NULL COMMENT '被检查的 Release Candidate 标识',
  `status` varchar(16) NOT NULL COMMENT '六项规则聚合结论：PASS 或 FAIL',
  `checks_json` json NOT NULL COMMENT '按规则名保存的确定性结论与失败事实',
  `checked_at` datetime(6) NOT NULL COMMENT '规则执行并固化快照的 UTC 时间',
  `checked_by_type` varchar(16) NOT NULL COMMENT '检查发起者类型：USER 或 AGENT',
  `checked_by_id` bigint unsigned NOT NULL COMMENT '发起检查的用户或 Agent Run 标识',
  `resource_versions_json` json NOT NULL COMMENT '检查时 Release、Item、Pipeline、Test Run 与策略版本指纹',
  PRIMARY KEY (`id`),
  KEY `idx_release_prechecks_release_time` (`release_id`,`checked_at`),
  KEY `fk_release_prechecks_organization` (`organization_id`),
  CONSTRAINT `fk_release_prechecks_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_release_prechecks_release` FOREIGN KEY (`release_id`) REFERENCES `releases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='由后端规则计算且写入后不可修改的发布前检查快照';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `release_prechecks` WRITE;
/*!40000 ALTER TABLE `release_prechecks` DISABLE KEYS */;
/*!40000 ALTER TABLE `release_prechecks` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `releases`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `releases` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Release Candidate 递增标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '发布所属公司标识',
  `version_name` varchar(128) NOT NULL COMMENT '公司与环境内唯一的发布版本名',
  `environment` varchar(64) NOT NULL COMMENT '候选发布的目标环境；本会话不触发部署',
  `status` varchar(24) NOT NULL COMMENT '发布状态：DRAFT、PRECHECKED、READY_FOR_APPROVAL、APPROVED、DEPLOYING、RELEASED 或 FAILED',
  `release_note` text COMMENT '人工或 Agent 生成并可编辑的 Release Note；未填写时为空',
  `release_note_document_id` bigint unsigned DEFAULT NULL COMMENT '可选的不可变 Release Note 文档引用；未关联时为空',
  `policy_snapshot_json` json NOT NULL COMMENT '创建时固化的发布检查策略',
  `created_by` bigint unsigned NOT NULL COMMENT '创建 Release Candidate 的用户标识',
  `created_at` datetime(6) NOT NULL COMMENT 'Release Candidate 创建 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT 'Release Candidate 最近修改 UTC 时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'Release Candidate 并发修改与快照失效判断版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_releases_organization_version_environment` (`organization_id`,`version_name`,`environment`),
  KEY `fk_releases_note_document` (`release_note_document_id`),
  KEY `fk_releases_creator` (`created_by`),
  KEY `idx_releases_organization` (`organization_id`,`id`),
  CONSTRAINT `fk_releases_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_releases_note_document` FOREIGN KEY (`release_note_document_id`) REFERENCES `documents` (`id`),
  CONSTRAINT `fk_releases_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='独立聚合交付资产、检查策略与版本的 Release Candidate';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `releases` WRITE;
/*!40000 ALTER TABLE `releases` DISABLE KEYS */;
/*!40000 ALTER TABLE `releases` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `requirement_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `requirement_details` (
  `work_item_id` bigint unsigned NOT NULL COMMENT '对应 Requirement Work Item 的稳定标识且同时作为主键',
  `organization_id` bigint unsigned NOT NULL COMMENT '需求详情所属公司标识',
  `goal` longtext NOT NULL COMMENT '需求希望达成的业务目标；空字符串表示材料尚未满足提交条件',
  `in_scope` longtext NOT NULL COMMENT '本次需求明确包含的范围；空字符串表示材料尚未满足提交条件',
  `out_of_scope` longtext NOT NULL COMMENT '本次需求明确排除的范围；允许空字符串表示尚无排除项',
  `acceptance_criteria_json` json NOT NULL COMMENT '结构化验收标准数组；空数组表示材料尚未满足提交条件',
  `business_value` longtext NOT NULL COMMENT '需求的业务价值说明；允许空字符串表示尚未补充',
  `updated_at` datetime(6) NOT NULL COMMENT 'Requirement 结构化材料最近更新时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'Requirement 结构化材料编辑使用的乐观锁版本',
  PRIMARY KEY (`work_item_id`),
  KEY `idx_requirement_details_organization` (`organization_id`,`work_item_id`),
  CONSTRAINT `fk_requirement_details_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_requirement_details_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Requirement 的结构化产品材料；作为确定性产品评审 Guard 的事实来源';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `requirement_details` WRITE;
/*!40000 ALTER TABLE `requirement_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `requirement_details` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `requirement_participants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `requirement_participants` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '需求角色参与关系的稳定业务标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '需求参与关系所属公司标识',
  `requirement_id` bigint unsigned NOT NULL COMMENT '被关联人员参与交付的 Requirement 标识',
  `role_code` varchar(32) NOT NULL COMMENT '人员在该需求承担的角色；仅允许 PRODUCT、UX、DEVELOPER、QA',
  `user_id` bigint unsigned NOT NULL COMMENT '承担该需求角色的有效组织成员用户标识',
  `assigned_by` bigint unsigned NOT NULL COMMENT '最近设置该参与人的操作者用户标识',
  `created_at` datetime(6) NOT NULL COMMENT '参与关系首次创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '参与关系最近更新的 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_requirement_participants_role` (`requirement_id`,`role_code`),
  KEY `idx_requirement_participants_user` (`organization_id`,`user_id`,`requirement_id`),
  KEY `fk_requirement_participants_user` (`user_id`),
  KEY `fk_requirement_participants_assigned_by` (`assigned_by`),
  CONSTRAINT `fk_requirement_participants_assigned_by` FOREIGN KEY (`assigned_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_requirement_participants_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_requirement_participants_requirement` FOREIGN KEY (`requirement_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `fk_requirement_participants_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_requirement_participants_role` CHECK ((`role_code` in (_utf8mb4'PRODUCT',_utf8mb4'UX',_utf8mb4'DEVELOPER',_utf8mb4'QA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Requirement 按产品、体验、开发和测试角色关联组织成员的事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `requirement_participants` WRITE;
/*!40000 ALTER TABLE `requirement_participants` DISABLE KEYS */;
/*!40000 ALTER TABLE `requirement_participants` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `review_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_records` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '评审记录的稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '评审记录所属公司标识',
  `work_item_id` bigint unsigned NOT NULL COMMENT '本次评审对应的 Requirement Work Item 标识',
  `review_type` varchar(40) NOT NULL COMMENT '评审类型；本轮写入 PRODUCT_REVIEW',
  `status` varchar(24) NOT NULL COMMENT '评审状态；SUBMITTED 表示等待产品评审，REJECTED 表示已退回',
  `reviewer_user_id` bigint unsigned DEFAULT NULL COMMENT '作出评审结论的用户；仅提交等待评审时为空',
  `comment` varchar(1000) DEFAULT NULL COMMENT '评审意见；没有意见时为空',
  `checklist_json` json NOT NULL COMMENT '评审时固化的结构化检查清单；本轮产品提交使用空对象',
  `artifact_version_json` json NOT NULL COMMENT '评审引用的交付物版本快照；本轮尚无文档版本时使用空对象',
  `created_at` datetime(6) NOT NULL COMMENT '评审记录创建的 UTC 时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_records_work_item` (`work_item_id`,`review_type`,`status`,`id`),
  KEY `fk_review_records_reviewer` (`reviewer_user_id`),
  KEY `fk_review_records_organization` (`organization_id`),
  CONSTRAINT `fk_review_records_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_review_records_reviewer` FOREIGN KEY (`reviewer_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_review_records_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Requirement 产品与体验评审的不可变结论记录';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `review_records` WRITE;
/*!40000 ALTER TABLE `review_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `review_records` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `role_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permissions` (
  `role_id` bigint unsigned NOT NULL COMMENT '拥有该权限的角色标识',
  `permission_id` bigint unsigned NOT NULL COMMENT '授予角色的原子权限标识',
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `fk_role_permissions_permission` (`permission_id`),
  CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`),
  CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色与权限的多对多授权事实；没有映射即不允许';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `role_permissions` WRITE;
/*!40000 ALTER TABLE `role_permissions` DISABLE KEYS */;
INSERT INTO `role_permissions` VALUES (1,3),(2,3),(1,4),(2,4),(1,7),(2,7),(3,7),(4,7),(5,7),(6,7),(7,7),(1,8),(2,8),(3,8),(1,9),(2,9),(3,9),(1,10),(2,10),(3,10),(4,10),(5,10),(6,10),(7,10),(1,11),(2,11),(4,11),(1,12),(2,12),(4,12),(1,13),(2,13),(3,13),(4,13),(5,13),(6,13),(7,13),(1,14),(2,14),(3,14),(4,14),(5,14),(6,14),(1,15),(2,15),(3,15),(4,15),(5,15),(6,15),(1,16),(2,16),(3,16),(1,17),(2,17),(3,17),(4,17),(5,17),(6,17),(7,17),(1,18),(2,18),(3,18),(4,18),(1,19),(2,19),(3,19),(4,19),(1,20),(2,20),(3,20),(4,20),(1,21),(2,21),(3,21),(4,21),(1,22),(2,22),(3,22),(1,23),(2,23),(3,23),(4,23),(1,24),(2,24),(7,24),(1,25),(2,25),(1,26),(2,26),(5,26),(1,27),(2,27),(5,27),(1,28),(2,28),(5,28),(1,29),(2,29),(6,29),(1,30),(2,30),(6,30),(1,31),(2,31),(6,31),(1,32),(2,32),(5,32),(6,32),(1,33),(2,33),(6,33),(1,34),(2,34),(5,34),(6,34),(1,35),(2,35),(5,35),(1,36),(2,36),(6,36),(1,37),(2,37),(3,37),(5,37),(6,37),(7,37),(1,38),(2,38),(3,38),(6,38),(1,39),(2,39),(3,39),(6,39),(1,40),(2,40),(3,40);
/*!40000 ALTER TABLE `role_permissions` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '角色的稳定业务标识',
  `organization_id` bigint unsigned DEFAULT NULL COMMENT '自定义角色所属公司；系统角色为空',
  `organization_scope_key` bigint unsigned GENERATED ALWAYS AS (ifnull(`organization_id`,0)) STORED COMMENT '将系统角色空公司范围规范为 0，以便唯一约束覆盖空值',
  `code` varchar(64) NOT NULL COMMENT '角色稳定代码；初始化仅创建系统 OWNER',
  `name` varchar(120) NOT NULL COMMENT '角色的界面展示名称',
  `system_role` tinyint(1) NOT NULL COMMENT '是否为实例预置且不能按普通公司角色删除',
  `description` varchar(500) NOT NULL COMMENT '角色业务能力与适用范围说明',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_roles_organization_code` (`organization_scope_key`,`code`),
  KEY `idx_roles_code_system` (`code`,`system_role`),
  KEY `fk_roles_organization` (`organization_id`),
  CONSTRAINT `fk_roles_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可分配给公司成员的角色定义';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` (`id`, `organization_id`, `code`, `name`, `system_role`, `description`) VALUES (1,NULL,'OWNER','Owner',1,'公司所有者；拥有全部默认权限但不绕过 HIGH 审批'),(2,NULL,'ADMIN','Admin',1,'公司管理员；负责成员与权限管理'),(3,NULL,'PRODUCT','Product',1,'产品角色；负责需求管理'),(4,NULL,'UX','UX',1,'体验设计角色；负责 UX 工作范围'),(5,NULL,'DEVELOPER','Developer',1,'研发角色；负责开发工作范围'),(6,NULL,'QA','QA',1,'质量角色；负责测试工作范围'),(7,NULL,'RELEASE_APPROVER','Release Approver',1,'发布审批角色；负责发布审核范围');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `secrets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `secrets` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '加密凭据的内部标识，不作为普通资源 API 暴露',
  `organization_id` bigint unsigned NOT NULL COMMENT '凭据所属公司标识',
  `type` varchar(64) NOT NULL COMMENT '凭据用途类型，例如 GITLAB_TOKEN',
  `ciphertext` text NOT NULL COMMENT 'AES-256-GCM 密文与认证标签的 Base64 表示',
  `iv` varchar(64) NOT NULL COMMENT '本次加密唯一的 96 位随机 IV 的 Base64 表示',
  `key_version` int unsigned NOT NULL COMMENT '解密所需部署主密钥版本',
  `fingerprint` varchar(32) NOT NULL COMMENT '用于管理员识别轮换结果的不可逆 HMAC 短指纹',
  `created_at` datetime(6) NOT NULL COMMENT '凭据首次创建的 UTC 时间',
  `rotated_at` datetime(6) DEFAULT NULL COMMENT '该密文由轮换操作写入的 UTC 时间，首次保存为空',
  PRIMARY KEY (`id`),
  KEY `idx_secrets_organization_type` (`organization_id`,`type`),
  CONSTRAINT `fk_secrets_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仅供受控服务临时解密的公司外部凭据密文';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `secrets` WRITE;
/*!40000 ALTER TABLE `secrets` DISABLE KEYS */;
/*!40000 ALTER TABLE `secrets` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `source_control_operations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `source_control_operations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '外部写操作本地稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '源码操作所属公司标识',
  `work_item_id` bigint unsigned NOT NULL COMMENT '触发操作的 Dev Task',
  `operation_type` varchar(32) NOT NULL COMMENT '操作类型；本轮为 START_DEVELOPMENT',
  `idempotency_key` varchar(128) NOT NULL COMMENT '调用方提供的公司范围幂等键',
  `request_hash` varchar(64) NOT NULL COMMENT '用于拒绝同键不同参数的 SHA-256 摘要',
  `target_branch` varchar(255) NOT NULL DEFAULT '' COMMENT '启动开发请求冻结的目标分支；恢复时不得由当前仓库默认值漂移',
  `status` varchar(32) NOT NULL COMMENT 'PROCESSING、COMPLETED 或 FAILED',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '后台恢复尝试次数；不含原始同步请求',
  `next_attempt_at` datetime(6) DEFAULT NULL COMMENT '下次允许恢复的 UTC 时间；为空表示可立即检查',
  `last_error_code` varchar(64) DEFAULT NULL COMMENT '最近一次标准化 GitLab 错误码；不得保存 Token 或远端正文',
  `branch_id` bigint unsigned DEFAULT NULL COMMENT '完成后关联的本地分支快照',
  `merge_request_id` bigint unsigned DEFAULT NULL COMMENT '完成后关联的本地 MR 快照',
  `created_at` datetime(6) NOT NULL COMMENT '首次记录外部写意图的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '操作状态最近变化的 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_source_control_operation_company_key` (`organization_id`,`idempotency_key`),
  KEY `fk_source_control_operation_work_item` (`work_item_id`),
  KEY `fk_source_control_operation_branch` (`branch_id`),
  KEY `fk_source_control_operation_mr` (`merge_request_id`),
  KEY `idx_source_control_operations_reconcile` (`status`,`next_attempt_at`,`updated_at`),
  KEY `idx_source_control_operation_organization_item` (`organization_id`,`work_item_id`),
  CONSTRAINT `fk_source_control_operation_branch` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`),
  CONSTRAINT `fk_source_control_operation_mr` FOREIGN KEY (`merge_request_id`) REFERENCES `merge_requests` (`id`),
  CONSTRAINT `fk_source_control_operation_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_source_control_operation_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不支持远端强幂等时用于 reconcile 的外部写意图与结果';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `source_control_operations` WRITE;
/*!40000 ALTER TABLE `source_control_operations` DISABLE KEYS */;
/*!40000 ALTER TABLE `source_control_operations` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_cases`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_cases` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '测试用例递增标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '测试用例所属公司标识',
  `work_item_id` bigint unsigned NOT NULL COMMENT '用例覆盖的 Requirement 标识',
  `title` varchar(255) NOT NULL COMMENT '可读且可定位的用例标题',
  `preconditions` text NOT NULL COMMENT '执行前必须具备的条件；无条件时为空字符串',
  `steps_json` json NOT NULL COMMENT '按执行顺序保存的非空测试步骤数组',
  `expected_result` text NOT NULL COMMENT '验证通过时应观察到的结果',
  `priority` varchar(8) NOT NULL COMMENT 'QA 优先级：P0、P1 或 P2',
  `status` varchar(16) NOT NULL COMMENT '用例设计状态：ACTIVE 或 ARCHIVED',
  `created_by` bigint unsigned NOT NULL COMMENT '创建用例的用户标识',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '用例编辑使用的乐观锁版本',
  `created_at` datetime(6) NOT NULL COMMENT '用例创建 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '用例最近更新 UTC 时间',
  PRIMARY KEY (`id`),
  KEY `idx_test_cases_work_item_status` (`work_item_id`,`status`),
  KEY `fk_test_cases_creator` (`created_by`),
  KEY `fk_test_cases_organization` (`organization_id`),
  CONSTRAINT `fk_test_cases_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_test_cases_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_test_cases_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Requirement 的可复用测试设计';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_cases` WRITE;
/*!40000 ALTER TABLE `test_cases` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_cases` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_results` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '单个用例在一次运行中的结果标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '测试结果所属公司标识',
  `test_run_id` bigint unsigned NOT NULL COMMENT '结果所属的测试运行标识',
  `test_case_id` bigint unsigned NOT NULL COMMENT '被执行的测试用例标识',
  `status` varchar(16) NOT NULL COMMENT '结果状态：NOT_RUN、PASS、FAIL、BLOCKED 或 SKIPPED',
  `actual_result` text NOT NULL COMMENT '实际观察结果；未执行时为空字符串',
  `evidence_json` json NOT NULL COMMENT '证据 URL 或引用的字符串数组',
  `executed_by` bigint unsigned DEFAULT NULL COMMENT '最后执行或修改结果的用户；未执行时为空',
  `executed_at` datetime(6) DEFAULT NULL COMMENT '最后执行 UTC 时间；未执行时为空',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '并发结果更新使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_test_results_run_case` (`test_run_id`,`test_case_id`),
  KEY `fk_test_results_case` (`test_case_id`),
  KEY `fk_test_results_executor` (`executed_by`),
  KEY `fk_test_results_organization` (`organization_id`),
  CONSTRAINT `fk_test_results_case` FOREIGN KEY (`test_case_id`) REFERENCES `test_cases` (`id`),
  CONSTRAINT `fk_test_results_executor` FOREIGN KEY (`executed_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_test_results_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_test_results_run` FOREIGN KEY (`test_run_id`) REFERENCES `test_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Test Case 在特定 Test Run 中的一次执行事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_results` WRITE;
/*!40000 ALTER TABLE `test_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_results` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `test_runs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_runs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '一次测试执行的递增标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '测试运行所属公司标识',
  `requirement_id` bigint unsigned NOT NULL COMMENT '本次验证的 Requirement 标识',
  `environment` varchar(255) NOT NULL COMMENT '执行环境的可审计说明',
  `status` varchar(16) NOT NULL COMMENT '运行状态：DRAFT、IN_PROGRESS、COMPLETED 或 CANCELLED',
  `started_by` bigint unsigned NOT NULL COMMENT '发起运行的 QA 用户标识',
  `started_at` datetime(6) NOT NULL COMMENT '运行创建并开始的 UTC 时间',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '完成或取消 UTC 时间；进行中为空',
  `summary_json` json DEFAULT NULL COMMENT '完成时固化的统计；reopen 后清空并重新计算',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '运行状态变更使用的乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_test_runs_requirement_status` (`requirement_id`,`status`),
  KEY `fk_test_runs_starter` (`started_by`),
  KEY `fk_test_runs_organization` (`organization_id`),
  CONSTRAINT `fk_test_runs_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_test_runs_requirement` FOREIGN KEY (`requirement_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `fk_test_runs_starter` FOREIGN KEY (`started_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绑定一次执行集合及完成统计的测试运行';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `test_runs` WRITE;
/*!40000 ALTER TABLE `test_runs` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_runs` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '实例内用户的稳定业务标识',
  `email` varchar(320) NOT NULL COMMENT '用户输入并用于展示的电子邮箱地址',
  `normalized_email` varchar(320) NOT NULL COMMENT '去除首尾空白并小写后的登录唯一键',
  `display_name` varchar(120) NOT NULL COMMENT '界面展示的用户名称，不参与身份唯一性判断',
  `password_hash` varchar(100) NOT NULL COMMENT 'BCrypt 密码摘要，只允许服务端验证且禁止回显或记录日志',
  `status` varchar(32) NOT NULL COMMENT '用户生命周期状态；初始化用户固定为 ACTIVE',
  `failed_login_count` int unsigned NOT NULL DEFAULT '0' COMMENT '连续登录失败次数，由后续登录会话更新',
  `locked_until` datetime(6) DEFAULT NULL COMMENT '账户锁定截止 UTC 时间；为空表示未按时间锁定',
  `last_login_at` datetime(6) DEFAULT NULL COMMENT '最近一次成功登录的 UTC 时间；初始化时为空',
  `created_at` datetime(6) NOT NULL COMMENT '用户创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '用户最近一次更新的 UTC 时间',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '用户并发更新使用的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_users_normalized_email` (`normalized_email`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实例用户及其本地密码认证事实';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `webhook_deliveries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `webhook_deliveries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Webhook delivery 本地稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT 'GitLab Webhook 投递所属公司标识',
  `connection_id` bigint unsigned NOT NULL COMMENT '接收 delivery 的 GitLab 连接',
  `delivery_key` varchar(128) NOT NULL COMMENT '远端 UUID 或基于事件事实计算的确定性键',
  `event_type` varchar(128) NOT NULL COMMENT 'GitLab X-Gitlab-Event 标准事件类型',
  `payload_hash` char(64) NOT NULL COMMENT '受限原始 payload 的 SHA-256 摘要',
  `payload` json DEFAULT NULL COMMENT '仅异步处理期间短期保存的受限原始 JSON；完成后清空',
  `status` varchar(24) NOT NULL COMMENT 'PENDING、PROCESSING、PROCESSED、IGNORED、FAILED 或 DEAD',
  `attempts` int unsigned NOT NULL DEFAULT '0' COMMENT '已失败处理次数；达到上限后转 DEAD',
  `next_attempt_at` datetime(6) NOT NULL COMMENT '下次可领取时间或 PROCESSING 租约到期时间',
  `error_message` varchar(512) DEFAULT NULL COMMENT '最近一次失败的截断描述；不得包含原始 payload',
  `received_at` datetime(6) NOT NULL COMMENT 'ForgeAI 首次接收 delivery 的 UTC 时间',
  `processed_at` datetime(6) DEFAULT NULL COMMENT '成功处理或安全忽略的 UTC 时间；待处理时为空',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_webhook_deliveries_connection_key` (`connection_id`,`delivery_key`),
  KEY `idx_webhook_deliveries_claim` (`status`,`next_attempt_at`,`id`),
  KEY `fk_webhook_deliveries_organization` (`organization_id`),
  CONSTRAINT `fk_webhook_deliveries_connection` FOREIGN KEY (`connection_id`) REFERENCES `gitlab_connections` (`id`),
  CONSTRAINT `fk_webhook_deliveries_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='至少一次接收、幂等领取与退避处理的 GitLab Webhook 队列';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `webhook_deliveries` WRITE;
/*!40000 ALTER TABLE `webhook_deliveries` DISABLE KEYS */;
/*!40000 ALTER TABLE `webhook_deliveries` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `work_item_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_item_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '工作项活动事件的单调标识；同一工作项按此字段排序',
  `organization_id` bigint unsigned NOT NULL COMMENT '工作项事件所属公司标识',
  `work_item_id` bigint unsigned NOT NULL COMMENT '事件所属的 Work Item 聚合标识',
  `event_type` varchar(64) NOT NULL COMMENT '稳定的机器可读事件类型；本轮使用工作流 Action 名称',
  `from_status` varchar(40) NOT NULL COMMENT '动作执行前的主生命周期状态',
  `to_status` varchar(40) NOT NULL COMMENT '动作成功后的主生命周期状态',
  `actor_type` varchar(16) NOT NULL COMMENT '动作主体类型；本轮公开 API 固定为 USER',
  `actor_id` bigint unsigned NOT NULL COMMENT '执行动作的用户标识',
  `reason` varchar(1000) DEFAULT NULL COMMENT '动作原因；不要求原因的动作可以为空',
  `metadata_json` json NOT NULL COMMENT '事件附加机器数据；没有附加数据时使用空对象',
  `idempotency_key` varchar(128) NOT NULL COMMENT '调用方提供的重试标识；同一 Work Item 内不得复用',
  `created_at` datetime(6) NOT NULL COMMENT '事件与状态转换在同一事务内成功的 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_work_item_events_idempotency` (`work_item_id`,`idempotency_key`),
  KEY `idx_work_item_events_timeline` (`work_item_id`,`id`),
  KEY `fk_work_item_events_actor` (`actor_id`),
  KEY `fk_work_item_events_organization` (`organization_id`),
  CONSTRAINT `fk_work_item_events_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_work_item_events_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_work_item_events_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Work Item 状态转换的追加写活动历史；当前状态仍以 work_items 为事实源';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `work_item_events` WRITE;
/*!40000 ALTER TABLE `work_item_events` DISABLE KEYS */;
/*!40000 ALTER TABLE `work_item_events` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `work_item_labels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_item_labels` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '工作项标签关系的稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '标签所属公司标识',
  `work_item_id` bigint unsigned NOT NULL COMMENT '被分类的工作项标识',
  `label` varchar(40) NOT NULL COMMENT '服务端识别的稳定分类；跳过 UX 仅接受 BACKEND_ONLY、OPS 或 INTERNAL_TECH',
  `created_by` bigint unsigned NOT NULL COMMENT '添加分类的用户标识，用于追溯',
  `created_at` datetime(6) NOT NULL COMMENT '分类添加的 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_work_item_labels_item_label` (`work_item_id`,`label`),
  KEY `idx_work_item_labels_scope` (`organization_id`,`work_item_id`),
  KEY `fk_work_item_labels_created_by` (`created_by`),
  CONSTRAINT `fk_work_item_labels_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_work_item_labels_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_work_item_labels_work_item` FOREIGN KEY (`work_item_id`) REFERENCES `work_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作项的显式业务分类；用于可解释的确定性策略判断';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `work_item_labels` WRITE;
/*!40000 ALTER TABLE `work_item_labels` DISABLE KEYS */;
/*!40000 ALTER TABLE `work_item_labels` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `work_item_relations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_item_relations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '工作项非树状关系的稳定标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '工作项关系所属公司标识',
  `source_id` bigint unsigned NOT NULL COMMENT '关系起点工作项标识',
  `target_id` bigint unsigned NOT NULL COMMENT '关系终点工作项标识；不得等于起点',
  `relation_type` varchar(32) NOT NULL COMMENT '关系语义；支持 DEPENDS_ON、BLOCKS 和 RELATES_TO',
  `created_by` bigint unsigned NOT NULL COMMENT '创建关系的用户标识',
  `created_at` datetime(6) NOT NULL COMMENT '关系创建的 UTC 时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_work_item_relations_direction` (`source_id`,`target_id`,`relation_type`),
  KEY `idx_work_item_relations_scope_source` (`organization_id`,`source_id`),
  KEY `idx_work_item_relations_scope_target` (`organization_id`,`target_id`),
  KEY `fk_work_item_relations_target` (`target_id`),
  KEY `fk_work_item_relations_created_by` (`created_by`),
  CONSTRAINT `fk_work_item_relations_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_work_item_relations_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_work_item_relations_source` FOREIGN KEY (`source_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `fk_work_item_relations_target` FOREIGN KEY (`target_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `chk_work_item_relations_not_self` CHECK ((`source_id` <> `target_id`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Work Item 间有向非树状关系；树状从属仍只使用 work_items.parent_id';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `work_item_relations` WRITE;
/*!40000 ALTER TABLE `work_item_relations` DISABLE KEYS */;
/*!40000 ALTER TABLE `work_item_relations` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `work_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_items` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '工作项聚合的稳定业务标识',
  `organization_id` bigint unsigned NOT NULL COMMENT '工作项所属公司标识',
  `item_number` bigint unsigned NOT NULL COMMENT '公司内单调分配的正整数编号；逻辑删除后仍不可复用',
  `item_key` varchar(64) NOT NULL COMMENT '服务端使用公司短键与编号生成的展示标识，例如 FORGE-1',
  `type` varchar(32) NOT NULL COMMENT '工作项业务类型；本轮开放 REQUIREMENT、UX_TASK、DEV_TASK 和 QA_TASK',
  `title` varchar(255) NOT NULL COMMENT '工作项的简短可检索标题',
  `description` longtext NOT NULL COMMENT '工作项的详细说明；空字符串表示尚未补充',
  `status` varchar(40) NOT NULL COMMENT '由工作项类型确定的生命周期状态；本轮仅写入创建初值且不开放迁移',
  `priority` varchar(16) NOT NULL COMMENT '业务处理优先级；合法值由服务端 WorkItemPriority 定义',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '树状从属工作项标识；为空表示没有父项，本轮不开放关系写入',
  `assignee_user_id` bigint unsigned DEFAULT NULL COMMENT '当前负责人用户标识；为空表示尚未分配',
  `reporter_user_id` bigint unsigned NOT NULL COMMENT '创建工作项的会话用户标识，用于责任追溯而非单独授权',
  `due_at` datetime(6) DEFAULT NULL COMMENT '期望完成的 UTC 时间；为空表示没有截止日期',
  `severity` varchar(16) DEFAULT NULL COMMENT '缺陷严重级别；非 BUG 工作项为空且本轮不开放写入',
  `blocked_at` datetime(6) DEFAULT NULL COMMENT '进入阻塞状态的 UTC 时间；为空表示当前未阻塞，本轮不开放写入',
  `blocked_reason` varchar(1000) DEFAULT NULL COMMENT '阻塞原因；未阻塞时为空，本轮不开放写入',
  `blocked_by` bigint unsigned DEFAULT NULL COMMENT '最近执行阻塞操作的用户标识；未阻塞时为空，本轮不开放写入',
  `created_at` datetime(6) NOT NULL COMMENT '工作项创建的 UTC 时间',
  `updated_at` datetime(6) NOT NULL COMMENT '工作项最近一次基础字段修改的 UTC 时间',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '逻辑删除的 UTC 时间；为空表示对普通查询可见，删除不释放编号',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '工作项乐观锁版本；PATCH 的 expectedVersion 必须与其匹配',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_work_items_organization_number` (`organization_id`,`item_number`),
  UNIQUE KEY `uq_work_items_organization_key` (`organization_id`,`item_key`),
  KEY `idx_work_items_parent` (`parent_id`),
  KEY `fk_work_items_assignee` (`assignee_user_id`),
  KEY `fk_work_items_reporter` (`reporter_user_id`),
  KEY `fk_work_items_blocked_by` (`blocked_by`),
  KEY `idx_work_items_scope_page` (`organization_id`,`deleted_at`,`item_number` DESC) COMMENT '支持公司范围内未删除工作项的稳定倒序分页',
  KEY `idx_work_items_scope_type_status_page` (`organization_id`,`type`,`status`,`deleted_at`,`item_number` DESC) COMMENT '支持公司范围内按类型状态筛选的稳定倒序分页',
  KEY `idx_work_items_organization_type_status` (`organization_id`,`type`,`status`),
  KEY `idx_work_items_organization_assignee_status` (`organization_id`,`assignee_user_id`,`status`),
  CONSTRAINT `fk_work_items_assignee` FOREIGN KEY (`assignee_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_work_items_blocked_by` FOREIGN KEY (`blocked_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_work_items_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `fk_work_items_parent` FOREIGN KEY (`parent_id`) REFERENCES `work_items` (`id`),
  CONSTRAINT `fk_work_items_reporter` FOREIGN KEY (`reporter_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一承载 Requirement 与角色 Task 的业务聚合；状态流转和关系留待后续会话';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `work_items` WRITE;
/*!40000 ALTER TABLE `work_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `work_items` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
