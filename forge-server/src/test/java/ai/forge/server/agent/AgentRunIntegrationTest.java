package ai.forge.server.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.agent.application.AgentRunStore;
import ai.forge.server.agent.application.AgentRuntimeGateway;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.platform.agent.AgentServiceTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

class AgentRunIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 HTTP、Session 与 CSRF 链路验证 Run API。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析成功响应信封中的 Run 快照。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 仅用于搭建身份事实并断言持久化结果。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /* 清理跨测试共享的登录限流状态，避免用例数量增长后相互污染。 */
    @Autowired
    private StringRedisTemplate redisTemplate;

    /* 直接验证 Runtime 结构化结果到 MySQL Trace 的事务投影。 */
    @Autowired
    private AgentRunStore agentRunStore;

    /* 为内部增量回调签发与生产一致的 run-scoped credential。 */
    @Autowired
    private AgentServiceTokenProvider tokenProvider;

    private String ownerCookie;
    private long organizationId;


    @BeforeEach
    void initializeOrganization() throws Exception {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "outbox_events", "agent_messages", "agent_conversations", "agent_tool_calls",
                "approvals", "agent_events", "agent_steps", "agent_runs", "comments", "work_item_relations",
                "work_item_labels", "organization_policies", "work_item_events", "review_records",
                "requirement_details", "work_items", "organization_item_sequences",                 "organization_policies", "audit_logs", "member_roles", "organization_members", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        ResponseEntity<String> initialized = csrf().post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "owner@example.com",
                        "adminDisplayName", "Forge Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge"),
                null,
                String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ownerCookie = loginOwner();
        organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1", Long.class);
    }

    @AfterEach
    void waitForBackgroundDispatchBeforeTheNextDatabaseReset() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        while (jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM agent_runs WHERE status IN ('QUEUED','RUNNING')", Long.class) > 0
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
    }

    @Test
    void createsRunAndFakeDispatcherPersistsOrderedTerminalTraceWithoutPromptBody() throws Exception {
        String sensitiveMessage = "请分析 token=should-not-be-persisted";
        ResponseEntity<String> created = createRun(sensitiveMessage, "request-one");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode initial = data(created);
        String runId = initial.get("id").asText();
        assertThat(runId).hasSize(26);

        JsonNode terminal = awaitTerminal(runId);
        assertThat(terminal.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(terminal.get("lastSequence").asLong()).isEqualTo(7);
        assertThat(terminal.get("steps")).hasSize(2);
        assertThat(terminal.get("steps").get(0).get("status").asText()).isEqualTo("SUCCEEDED");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT message_redacted FROM agent_runs WHERE id = ?", String.class, runId))
                .doesNotContain("should-not-be-persisted")
                .contains("chars");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT sequence FROM agent_events WHERE run_id = ? ORDER BY sequence", Long.class, runId))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM agent_events WHERE run_id = ? ORDER BY sequence", String.class, runId))
                .containsExactly("agent.queued", "agent.started", "step.started", "step.completed",
                        "step.completed", "message.completed", "agent.completed");
    }

    @Test
    void failedToolObservationCanStillCompleteRunTrace() {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0XY";
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email='owner@example.com'", Long.class);
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ASK, "User request (24 chars, content redacted)",
                "failed-tool-trace", "request-hash", "request-create");
        agentRunStore.start(organizationId, runId, "request-start");

        agentRunStore.complete(organizationId, runId, "request-complete",
                "需求创建失败，服务器返回 501 错误。",
                List.of("定义测试需求名称", "编写测试需求内容", "创建需求对象", "保存需求记录"),
                List.of(new AgentRuntimeGateway.ToolCallResult(
                        "create_requirement", "call-1", "FAILED", "SERVER_ERROR")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM agent_runs WHERE id=?", String.class, runId))
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void successfulToolRunCompletesAfterStreamingAnswerDeltas() {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0XV";
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email='owner@example.com'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO agent_conversations(organization_id,user_id,title,created_at,updated_at,version) "
                        + "VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                organizationId, userId, "完成事务回归");
        long conversationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (4 chars, content redacted)",
                "successful-tool-trace", "request-hash", "request-create");
        jdbcTemplate.update(
                "INSERT INTO agent_messages(conversation_id,sender,body,run_id,created_at) "
                        + "VALUES(?,'USER','新建需求',?,UTC_TIMESTAMP(6))",
                conversationId, runId);
        agentRunStore.start(organizationId, runId, "request-start");
        agentRunStore.appendMessageDelta(organizationId, runId, "已创建需求：");
        agentRunStore.appendMessageDelta(organizationId, runId, "REQ-20（ID 20），当前状态为草稿，版本 0。");

        agentRunStore.complete(organizationId, runId, "request-complete",
                "已创建需求：REQ-20（ID 20），当前状态为草稿，版本 0。",
                List.of("理解产品请求", "整理受控上下文", "形成可回溯结果"),
                List.of(new AgentRuntimeGateway.ToolCallResult(
                        "create_requirement", "call-1", "SUCCEEDED", null)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM agent_runs WHERE id=?", String.class, runId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT body FROM agent_messages WHERE run_id=? AND sender='AGENT'", String.class, runId))
                .contains("REQ-20");
    }

    @Test
    void internalRuntimeCallbackPersistsReplayableMessageDelta() {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0XZ";
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email='owner@example.com'", Long.class);
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ASK, "User request (8 chars, content redacted)",
                "stream-callback", "request-hash", "request-create");
        agentRunStore.start(organizationId, runId, "request-start");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.createRunToken(Instant.now(), runId, organizationId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/internal/v1/agent-runs/" + runId + "/message-deltas",
                new HttpEntity<>(Map.of("delta", "增量正文"), headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM agent_events WHERE run_id=? ORDER BY sequence DESC LIMIT 1",
                String.class, runId)).isEqualTo("message.delta");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.delta')) FROM agent_events "
                        + "WHERE run_id=? ORDER BY sequence DESC LIMIT 1",
                String.class, runId)).isEqualTo("增量正文");
    }

    @Test
    void internalRuntimeCallbackPersistsReplayableReasoningDelta() {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0XA";
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email='owner@example.com'", Long.class);
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ASK, "User request (8 chars, content redacted)",
                "reasoning-stream-callback", "request-hash", "request-create");
        agentRunStore.start(organizationId, runId, "request-start");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.createRunToken(Instant.now(), runId, organizationId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/internal/v1/agent-runs/" + runId + "/reasoning-deltas",
                new HttpEntity<>(Map.of("delta", "先读取权威需求，再形成结果。"), headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM agent_events WHERE run_id=? ORDER BY sequence DESC LIMIT 1",
                String.class, runId)).isEqualTo("reasoning.delta");
    }

    @Test
    void cancelsActiveRunAndPersistsTerminalEvent() throws Exception {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0XW";
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email='owner@example.com'", Long.class);
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ASK, "User request (8 chars, content redacted)",
                "cancel-active", "request-hash", "request-create");
        agentRunStore.start(organizationId, runId, "request-start");

        ResponseEntity<String> response = csrf().post(
                "/api/v1/agent-runs/" + runId + ":cancel", Map.of(), ownerCookie, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode snapshot = data(response);
        assertThat(snapshot.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(snapshot.get("terminal").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM agent_events WHERE run_id=? ORDER BY sequence DESC LIMIT 1",
                String.class, runId)).isEqualTo("agent.cancelled");
    }

    @Test
    void clientRequestIdIsIdempotentForTheSameUserAndOrganization() throws Exception {
        JsonNode first = data(createRun("first", "same-request"));
        JsonNode repeated = data(createRun("first", "same-request"));

        assertThat(repeated.get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_runs", Long.class)).isEqualTo(1);
    }

    @Test
    void clientRequestIdCannotBeReusedWithDifferentInput() throws Exception {
        assertThat(createRun("first", "conflicting-request").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> conflict = createRun("different", "conflicting-request");

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("AGENT_RUN_IDEMPOTENCY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_runs", Long.class)).isEqualTo(1);
    }

    @Test
    void mediumToolConfirmationDefaultsToAllowAndExplicitPolicyIsPersisted() throws Exception {
        String defaultRunId = data(createRun("default policy", "policy-default")).get("id").asText();
        String allowedRunId = data(createRun("explicit allow", "policy-allow", "ALLOW")).get("id").asText();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT medium_tool_confirmation FROM agent_runs WHERE id = ?", String.class, defaultRunId))
                .isEqualTo("ALLOW");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT medium_tool_confirmation FROM agent_runs WHERE id = ?", String.class, allowedRunId))
                .isEqualTo("ALLOW");
    }

    @Test
    void reusedClientRequestIdWithDifferentMediumConfirmationIsRejectedAsConflict() throws Exception {
        assertThat(createRun("policy conflict", "policy-conflict").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> conflict = createRun("policy conflict", "policy-conflict", "DENY");

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("AGENT_RUN_IDEMPOTENCY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_runs", Long.class)).isEqualTo(1);
    }

    @Test
    void invalidMediumConfirmationValueIsRejected() throws Exception {
        ResponseEntity<String> rejected = createRun("invalid policy", "policy-invalid", "AUTO");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_runs", Long.class).longValue())
                .isEqualTo(0);
    }

    @Test
    void knownRunIdUsesCompanyScopeFromSession() throws Exception {
        String runId = data(createRun("scope", "scope-request")).get("id").asText();

        assertThat(getRun(runId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRun("00000000000000000000000000").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void requirementConversationStaysBoundAndRoutesEachMessageByCurrentStage() throws Exception {
        JsonNode requirement = data(csrf().post(
                "/api/v1/work-items",
                Map.of("type", "REQUIREMENT", "title", "连续 Agent 需求",
                        "description", "对话推动流程", "priority", "HIGH"),
                ownerCookie,
                String.class));
        long requirementId = requirement.get("id").asLong();
        JsonNode conversation = data(csrf().post(
                "/api/v1/agent-conversations",
                Map.of("title", "需求全流程"), ownerCookie, String.class));
        long conversationId = conversation.get("id").asLong();

        JsonNode productRun = data(csrf().post(
                "/api/v1/agent-conversations/" + conversationId + "/messages",
                Map.of("message", "整理并推进产品阶段", "workItemId", requirementId),
                ownerCookie,
                String.class));
        assertThat(productRun.get("skill").asText()).isEqualTo("PRODUCT");
        assertThat(productRun.get("workItemId").asLong()).isEqualTo(requirementId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT requirement_id FROM agent_conversations WHERE id=?", Long.class,
                conversationId)).isEqualTo(requirementId);

        JsonNode listedConversation = data(restTemplate.exchange(
                "/api/v1/agent-conversations",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers(ownerCookie)),
                String.class)).get(0);
        assertThat(listedConversation.get("requirementId").asLong()).isEqualTo(requirementId);
        assertThat(listedConversation.get("requirementKey").asText())
                .isEqualTo(requirement.get("itemKey").asText());

        jdbcTemplate.update("UPDATE work_items SET status='UX_IN_PROGRESS', version=version+1 WHERE id=?",
                requirementId);
        JsonNode uxRun = data(csrf().post(
                "/api/v1/agent-conversations/" + conversationId + "/messages",
                Map.of("message", "继续完成当前阶段"), ownerCookie, String.class));

        assertThat(uxRun.get("skill").asText()).isEqualTo("UX");
        assertThat(uxRun.get("workItemId").asLong()).isEqualTo(requirementId);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT skill FROM agent_runs WHERE work_item_id=? ORDER BY created_at",
                        String.class, requirementId))
                .containsExactly("PRODUCT", "UX");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT medium_tool_confirmation FROM agent_runs WHERE work_item_id=? ORDER BY created_at",
                        String.class, requirementId))
                .containsExactly("ALLOW", "ALLOW");

        JsonNode otherRequirement = data(csrf().post(
                "/api/v1/work-items",
                Map.of("type", "REQUIREMENT", "title", "另一需求", "description", "隔离",
                        "priority", "MEDIUM"),
                ownerCookie,
                String.class));
        ResponseEntity<String> rebound = csrf().post(
                "/api/v1/agent-conversations/" + conversationId + "/messages",
                Map.of("message", "切换需求", "workItemId", otherRequirement.get("id").asLong()),
                ownerCookie,
                String.class);
        assertThat(rebound.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void firstPromptGeneratesConversationTitleAndLaterPromptsKeepIt() throws Exception {
        JsonNode conversation = data(csrf().post(
                "/api/v1/agent-conversations", Map.of(), ownerCookie, String.class));
        long conversationId = conversation.get("id").asLong();
        assertThat(conversation.get("title").asText()).isEqualTo("新会话");

        csrf().post(
                "/api/v1/agent-conversations/" + conversationId + "/messages",
                Map.of("message", "  请帮我   梳理登录流程\n并补充异常场景  "), ownerCookie, String.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM agent_conversations WHERE id=?", String.class, conversationId))
                .isEqualTo("请帮我 梳理登录流程 并补充异常场景");

        csrf().post(
                "/api/v1/agent-conversations/" + conversationId + "/messages",
                Map.of("message", "第二轮不应覆盖标题"), ownerCookie, String.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM agent_conversations WHERE id=?", String.class, conversationId))
                .isEqualTo("请帮我 梳理登录流程 并补充异常场景");
    }

    @Test
    void ownerCanDeleteConversationAndMessagesButRunTraceRemains() throws Exception {
        JsonNode conversation = data(csrf().post(
                "/api/v1/agent-conversations", Map.of(), ownerCookie, String.class));
        long conversationId = conversation.get("id").asLong();
        JsonNode run = data(csrf().post(
                "/api/v1/agent-conversations/" + conversationId + "/messages",
                Map.of("message", "这条会话随后删除"), ownerCookie, String.class));

        ResponseEntity<String> deleted = csrf().delete(
                "/api/v1/agent-conversations/" + conversationId, ownerCookie, String.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_conversations WHERE id=?", Long.class, conversationId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_messages WHERE conversation_id=?", Long.class, conversationId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_runs WHERE id=?", Long.class, run.get("id").asText())).isOne();
        assertThat(csrf().delete(
                "/api/v1/agent-conversations/" + conversationId, ownerCookie, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createdRequirementAutomaticallyBindsItsConversation() throws Exception {
        JsonNode conversation = data(csrf().post(
                "/api/v1/agent-conversations",
                Map.of("title", "自动绑定新需求"), ownerCookie, String.class));
        long conversationId = conversation.get("id").asLong();
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email='owner@example.com'", Long.class);
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0XB";
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (8 chars, content redacted)",
                "auto-bind-created-requirement", "request-hash", "request-create");
        jdbcTemplate.update(
                "INSERT INTO agent_messages(conversation_id,sender,body,run_id,created_at) "
                        + "VALUES(?,'USER','创建需求',?,UTC_TIMESTAMP(6))",
                conversationId, runId);
        JsonNode requirement = data(csrf().post(
                "/api/v1/work-items",
                Map.of("type", "REQUIREMENT", "title", "Agent 新建需求",
                        "description", "自动绑定", "priority", "MEDIUM"),
                ownerCookie, String.class));
        agentRunStore.bindConversationToRequirement(
                organizationId, runId, requirement.get("id").asLong());

        Long requirementId = jdbcTemplate.queryForObject(
                "SELECT requirement_id FROM agent_conversations WHERE id=?",
                Long.class, conversationId);
        assertThat(requirementId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT type FROM work_items WHERE id=?", String.class, requirementId))
                .isEqualTo("REQUIREMENT");
    }

    @Test
    void terminalStreamReplaysAfterLastEventIdAndThenCloses() throws Exception {
        String runId = data(createRun("stream", "stream-request")).get("id").asText();
        JsonNode terminal = awaitTerminal(runId);
        assertThat(terminal.get("lastSequence").asLong()).isEqualTo(7);

        org.springframework.http.HttpHeaders headers = headers(ownerCookie);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        headers.set("Last-Event-ID", "2");
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/agent-runs/" + runId + "/events",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getBody())
                .contains("id:3", "event:step.started", "id:4", "event:step.completed",
                        "id:5", "event:step.completed", "id:6", "event:message.completed",
                        "id:7", "event:agent.completed")
                .doesNotContain("id:1", "id:2")
                .doesNotContain("should-not-be-persisted");

        ResponseEntity<String> caughtUp = restTemplate.exchange(
                "/api/v1/agent-runs/" + runId + "/events?afterSequence=" + terminal.get("lastSequence").asLong(),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);
        assertThat(caughtUp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(caughtUp.getBody()).isNullOrEmpty();
    }

    @Test
    void unknownStreamIsHiddenAsNotFound() {
        String runId = "00000000000000000000000000";
        org.springframework.http.HttpHeaders headers = headers(ownerCookie);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/agent-runs/" + runId + "/events",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("RESOURCE_NOT_FOUND");
    }

    @Test
    void conversationRunListReturnsAscendingRunsWithTriggerAndAnswer() throws Exception {
        JsonNode conversation = data(csrf().post(
                "/api/v1/agent-conversations", Map.of(), ownerCookie, String.class));
        long conversationId = conversation.get("id").asLong();
        long userId = ownerUserId();

        String firstRun = "01M2FBKQYQ6QNJ8FZH92PZP0X1";
        String secondRun = "01M2FBKQYQ6QNJ8FZH92PZP0X2";
        agentRunStore.create(firstRun, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (4 chars, content redacted)",
                "conv-list-1", "hash-1", "req-1");
        jdbcTemplate.update(
                "INSERT INTO agent_messages(conversation_id,sender,body,run_id,created_at) "
                        + "VALUES(?,'USER','第一轮指令',?,UTC_TIMESTAMP(6))",
                conversationId, firstRun);
        agentRunStore.start(organizationId, firstRun, "req-1-start");
        agentRunStore.complete(organizationId, firstRun, "req-1-complete", "第一轮回答",
                List.of("整理需求"), List.of());

        agentRunStore.create(secondRun, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (4 chars, content redacted)",
                "conv-list-2", "hash-2", "req-2");
        jdbcTemplate.update(
                "INSERT INTO agent_messages(conversation_id,sender,body,run_id,created_at) "
                        + "VALUES(?,'USER','第二轮指令',?,UTC_TIMESTAMP(6))",
                conversationId, secondRun);
        agentRunStore.start(organizationId, secondRun, "req-2-start");

        JsonNode runs = data(getConversationRuns(conversationId));

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).get("runId").asText()).isEqualTo(firstRun);
        assertThat(runs.get(0).get("skill").asText()).isEqualTo("PRODUCT");
        assertThat(runs.get(0).get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(runs.get(0).get("terminal").asBoolean()).isTrue();
        assertThat(runs.get(0).get("triggerMessage").asText()).isEqualTo("第一轮指令");
        assertThat(runs.get(0).get("answer").asText()).isEqualTo("第一轮回答");
        assertThat(runs.get(1).get("runId").asText()).isEqualTo(secondRun);
        assertThat(runs.get(1).get("terminal").asBoolean()).isFalse();
        assertThat(runs.get(1).get("answer").isNull()).isTrue();

        agentRunStore.cancel(organizationId, secondRun, "req-2-cancel");
    }

    @Test
    void conversationRunListHidesUnknownAndForeignConversationsAsNotFound() throws Exception {
        long otherUserId = insertForeignUser();
        jdbcTemplate.update(
                "INSERT INTO agent_conversations(organization_id,user_id,title,created_at,updated_at,version) "
                        + "VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                organizationId, otherUserId, "他人会话");
        long foreignConversationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        assertThat(getConversationRuns(foreignConversationId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getConversationRuns(999999999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void conversationWithoutRunsReturnsEmptyArray() throws Exception {
        JsonNode conversation = data(csrf().post(
                "/api/v1/agent-conversations", Map.of(), ownerCookie, String.class));
        long conversationId = conversation.get("id").asLong();

        JsonNode runs = data(getConversationRuns(conversationId));

        assertThat(runs).isEmpty();
    }

    @Test
    void runTraceAggregatesThinkingToolsApprovalAndTerminalForOwner() throws Exception {
        long userId = ownerUserId();
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0X3";
        String approvalId = "01M2FBKQYQ6QNJ8FZH92PZP0Y1";
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (4 chars, content redacted)",
                "trace-aggregate", "hash", "req-create");

        insertEvent(runId, 2, "thinking.delta",
                Map.of("delta", "先理解", "node", "create_plan"));
        insertEvent(runId, 3, "thinking.delta",
                Map.of("delta", "需求意图。", "node", "create_plan"));
        insertEvent(runId, 4, "reasoning.delta", Map.of("delta", "按计划执行"));
        insertEvent(runId, 5, "tool.completed", Map.of("stepNo", 2, "name", "create_requirement",
                "status", "SUCCEEDED", "summary", "已创建"));
        insertEvent(runId, 6, "approval.approved", Map.of("approvalId", approvalId, "status", "APPROVED"));
        insertEvent(runId, 7, "message.completed", Map.of("content", "已创建需求"));
        insertEvent(runId, 8, "agent.completed", Map.of("status", "SUCCEEDED", "summary", "已创建需求"));
        jdbcTemplate.update(
                "INSERT INTO agent_tool_calls(run_id,organization_id,tool_call_id,tool_name,tool_version,"
                        + "risk_level,status,arguments_json,result_json,idempotency_key,created_at,finished_at) "
                        + "VALUES(?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                runId, organizationId, "call-1", "create_requirement", 1, "MEDIUM", "SUCCEEDED",
                "{\"title\":\"登录需求\"}", "{\"id\":21}", runId + ":call-1");
        jdbcTemplate.update(
                "INSERT INTO approvals(id,organization_id,run_id,tool_call_id,type,risk_level,status,"
                        + "requested_by,approver_user_id,tool_name,tool_version,argument_hash,"
                        + "frozen_arguments_encrypted,resource_versions_json,reason,expires_at,decided_at,"
                        + "version,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,"
                        + "DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE),UTC_TIMESTAMP(6),0,UTC_TIMESTAMP(6))",
                approvalId, organizationId, runId, "call-1", "TOOL_EXECUTION", "MEDIUM", "APPROVED",
                userId, userId, "create_requirement", 1, "argument-hash", "encrypted-arguments",
                "[]", "需要人工确认");
        jdbcTemplate.update(
                "UPDATE agent_runs SET status='SUCCEEDED', "
                        + "started_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 3 SECOND), finished_at=UTC_TIMESTAMP(6), "
                        + "last_sequence=8 WHERE id=?",
                runId);

        JsonNode trace = data(getTrace(runId));

        assertThat(trace.get("runId").asText()).isEqualTo(runId);
        assertThat(trace.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(trace.get("terminal").asBoolean()).isTrue();
        JsonNode nodes = trace.get("nodes");

        JsonNode thinking = nodeOfKind(nodes, "THINKING");
        assertThat(thinking.get("detail").get("text").asText()).isEqualTo("先理解需求意图。");
        assertThat(thinking.get("detail").get("node").asText()).isEqualTo("create_plan");

        assertThat(nodeOfKind(nodes, "REASONING").get("detail").get("text").asText()).isEqualTo("按计划执行");

        JsonNode tool = nodeOfKind(nodes, "TOOL");
        assertThat(tool.get("detail").get("toolVersion").asInt()).isEqualTo(1);
        assertThat(tool.get("detail").get("riskLevel").asText()).isEqualTo("MEDIUM");
        assertThat(tool.get("detail").get("arguments").get("title").asText()).isEqualTo("登录需求");
        assertThat(tool.get("detail").get("result").get("id").asInt()).isEqualTo(21);

        JsonNode approval = nodeOfKind(nodes, "APPROVAL");
        assertThat(approval.get("detail").get("status").asText()).isEqualTo("APPROVED");
        assertThat(approval.get("detail").get("decidedBy").asLong()).isEqualTo(userId);

        JsonNode terminal = nodeOfKind(nodes, "TERMINAL");
        assertThat(terminal.get("detail").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(terminal.get("detail").get("durationMs").asLong()).isGreaterThan(0L);
    }

    @Test
    void runTraceReturnsPersistedNodesForUnfinishedRun() throws Exception {
        long userId = ownerUserId();
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0X7";
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (4 chars, content redacted)",
                "trace-running", "hash", "req-create");
        agentRunStore.start(organizationId, runId, "req-start");
        agentRunStore.appendThinkingDelta(organizationId, runId, "正在思考", "create_plan");

        JsonNode trace = data(getTrace(runId));

        assertThat(trace.get("status").asText()).isEqualTo("RUNNING");
        assertThat(trace.get("terminal").asBoolean()).isFalse();
        assertThat(nodeOfKind(trace.get("nodes"), "THINKING").get("detail").get("text").asText())
                .isEqualTo("正在思考");

        agentRunStore.cancel(organizationId, runId, "req-cancel");
    }

    @Test
    void runTraceIsHiddenAsNotFoundForNonOwnerAndCrossCompany() throws Exception {
        long userId = ownerUserId();
        long otherUserId = insertForeignUser();
        long otherOrganizationId = insertForeignOrganization(userId);

        String foreignUserRun = "01M2FBKQYQ6QNJ8FZH92PZP0X4";
        String foreignOrganizationRun = "01M2FBKQYQ6QNJ8FZH92PZP0X5";
        agentRunStore.create(foreignUserRun, organizationId, null, otherUserId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (4 chars, content redacted)",
                "trace-foreign-user", "hash", "req-fu");
        agentRunStore.create(foreignOrganizationRun, otherOrganizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (4 chars, content redacted)",
                "trace-foreign-org", "hash", "req-fo");

        assertThat(getTrace(foreignUserRun).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getTrace(foreignOrganizationRun).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getTrace("00000000000000000000000000").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        agentRunStore.cancel(organizationId, foreignUserRun, "req-fu-cancel");
        agentRunStore.cancel(otherOrganizationId, foreignOrganizationRun, "req-fo-cancel");
    }

    @Test
    void thinkingDeltaCallbackPersistsEventAndAdvancesSequence() {
        long userId = ownerUserId();
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0X6";
        agentRunStore.create(runId, organizationId, null, userId, AgentSkill.PRODUCT,
                MediumToolConfirmation.ALLOW, "User request (4 chars, content redacted)",
                "thinking-stream", "hash", "req-create");
        agentRunStore.start(organizationId, runId, "req-start");
        long before = jdbcTemplate.queryForObject(
                "SELECT last_sequence FROM agent_runs WHERE id=?", Long.class, runId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.createRunToken(Instant.now(), runId, organizationId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/internal/v1/agent-runs/" + runId + "/thinking-deltas",
                new HttpEntity<>(Map.of("delta", "模型思考片段", "node", "create_plan"), headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM agent_events WHERE run_id=? ORDER BY sequence DESC LIMIT 1",
                String.class, runId)).isEqualTo("thinking.delta");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.delta')) FROM agent_events "
                        + "WHERE run_id=? ORDER BY sequence DESC LIMIT 1",
                String.class, runId)).isEqualTo("模型思考片段");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.node')) FROM agent_events "
                        + "WHERE run_id=? ORDER BY sequence DESC LIMIT 1",
                String.class, runId)).isEqualTo("create_plan");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_sequence FROM agent_runs WHERE id=?", Long.class, runId)).isEqualTo(before + 1);

        agentRunStore.complete(organizationId, runId, "req-complete", "完成", List.of("计划"), List.of());
    }

    @Test
    void toolEventCallbackPersistsStartedAndCompletedEventsWhileRunning() throws Exception {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0T1";
        agentRunStore.create(runId, organizationId, null, ownerUserId(), AgentSkill.PRODUCT,
                MediumToolConfirmation.ASK, "User request (4 chars, content redacted)",
                "tool-event-stream", "hash", "req-create");
        agentRunStore.start(organizationId, runId, "req-start");
        long before = jdbcTemplate.queryForObject(
                "SELECT last_sequence FROM agent_runs WHERE id=?", Long.class, runId);
        HttpHeaders headers = runTokenHeaders(runId);
        String url = "/internal/v1/agent-runs/" + runId + "/tool-events";

        ResponseEntity<Void> started = restTemplate.postForEntity(url,
                new HttpEntity<>(Map.of(
                        "callId", "call-1",
                        "toolName", "create_requirement",
                        "event", "tool.started",
                        "status", "RUNNING"), headers), Void.class);
        ResponseEntity<Void> completed = restTemplate.postForEntity(url,
                new HttpEntity<>(Map.of(
                        "callId", "call-1",
                        "toolName", "create_requirement",
                        "event", "tool.completed",
                        "status", "SUCCEEDED",
                        "summary", "已创建需求",
                        "durationMs", 120), headers), Void.class);

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM agent_events WHERE run_id=? AND event_type LIKE 'tool.%' ORDER BY sequence",
                        String.class, runId))
                .containsExactly("tool.started", "tool.completed");
        Map<String, Object> payload = jdbcTemplate.queryForMap(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.callId')) AS call_id, "
                        + "JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.name')) AS name, "
                        + "JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.status')) AS status, "
                        + "JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.summary')) AS summary, "
                        + "JSON_EXTRACT(payload_json,'$.durationMs') AS duration_ms "
                        + "FROM agent_events WHERE run_id=? AND event_type='tool.completed'",
                runId);
        assertThat(payload.get("call_id")).isEqualTo("call-1");
        assertThat(payload.get("name")).isEqualTo("create_requirement");
        assertThat(payload.get("status")).isEqualTo("SUCCEEDED");
        assertThat(payload.get("summary")).isEqualTo("已创建需求");
        assertThat(payload.get("duration_ms").toString()).isEqualTo("120");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT last_sequence FROM agent_runs WHERE id=?", Long.class, runId))
                .isEqualTo(before + 2);

        agentRunStore.cancel(organizationId, runId, "req-cancel");
    }

    @Test
    void toolEventCallbackRejectsUnsupportedEventType() {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0T2";
        agentRunStore.create(runId, organizationId, null, ownerUserId(), AgentSkill.PRODUCT,
                MediumToolConfirmation.ASK, "User request (4 chars, content redacted)",
                "tool-event-invalid", "hash", "req-create");
        agentRunStore.start(organizationId, runId, "req-start");

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/internal/v1/agent-runs/" + runId + "/tool-events",
                new HttpEntity<>(Map.of(
                        "callId", "call-1",
                        "toolName", "create_requirement",
                        "event", "tool.exploded",
                        "status", "RUNNING"), runTokenHeaders(runId)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM agent_events WHERE run_id=? AND event_type LIKE 'tool.%'",
                        Long.class, runId))
                .isZero();

        agentRunStore.cancel(organizationId, runId, "req-cancel");
    }

    @Test
    void toolEventCallbackRejectsMissingOrInvalidBearer() {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0T3";
        agentRunStore.create(runId, organizationId, null, ownerUserId(), AgentSkill.PRODUCT,
                MediumToolConfirmation.ASK, "User request (4 chars, content redacted)",
                "tool-event-unauthorized", "hash", "req-create");
        agentRunStore.start(organizationId, runId, "req-start");
        String url = "/internal/v1/agent-runs/" + runId + "/tool-events";
        Map<String, Object> body = Map.of(
                "callId", "call-1",
                "toolName", "create_requirement",
                "event", "tool.started",
                "status", "RUNNING");

        HttpHeaders headerless = new HttpHeaders();
        headerless.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> missing = restTemplate.postForEntity(
                url, new HttpEntity<>(body, headerless), Void.class);

        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.setBearerAuth("not-a-jwt");
        invalidHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> invalid = restTemplate.postForEntity(
                url, new HttpEntity<>(body, invalidHeaders), Void.class);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        agentRunStore.cancel(organizationId, runId, "req-cancel");
    }

    @Test
    void toolEventCallbackRejectsRunIdMismatch() {
        String runId = "01M2FBKQYQ6QNJ8FZH92PZP0T4";
        agentRunStore.create(runId, organizationId, null, ownerUserId(), AgentSkill.PRODUCT,
                MediumToolConfirmation.ASK, "User request (4 chars, content redacted)",
                "tool-event-forbidden", "hash", "req-create");
        agentRunStore.start(organizationId, runId, "req-start");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.createRunToken(
                Instant.now(), "01234567890123456789012345", organizationId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/internal/v1/agent-runs/" + runId + "/tool-events",
                new HttpEntity<>(Map.of(
                        "callId", "call-1",
                        "toolName", "create_requirement",
                        "event", "tool.started",
                        "status", "RUNNING"), headers),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        agentRunStore.cancel(organizationId, runId, "req-cancel");
    }

    private HttpHeaders runTokenHeaders(String runId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.createRunToken(Instant.now(), runId, organizationId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private long ownerUserId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email='owner@example.com'", Long.class);
    }

    private long insertForeignUser() {
        jdbcTemplate.update(
                "INSERT INTO users(email,normalized_email,display_name,password_hash,status,created_at,"
                        + "updated_at,version) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                "other@example.com", "other@example.com", "Other Member",
                "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcdefghi", "ACTIVE");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email='other@example.com'", Long.class);
    }

    private long insertForeignOrganization(long ownerId) {
        jdbcTemplate.update(
                "INSERT INTO organizations(name,slug,owner_user_id,created_at,updated_at,version) "
                        + "VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                "Other Org", "other-org", ownerId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM organizations WHERE slug='other-org'", Long.class);
    }

    private void insertEvent(String runId, long sequence, String type, Map<String, Object> payload)
            throws Exception {
        jdbcTemplate.update(
                "INSERT INTO agent_events(run_id,sequence,event_type,request_id,payload_json,created_at) "
                        + "VALUES(?,?,?,?,CAST(? AS JSON),UTC_TIMESTAMP(6))",
                runId, sequence, type, "req-trace", objectMapper.writeValueAsString(payload));
    }

    private JsonNode nodeOfKind(JsonNode nodes, String kind) {
        for (JsonNode node : nodes) {
            if (kind.equals(node.get("kind").asText())) {
                return node;
            }
        }
        throw new AssertionError("missing trace node kind " + kind);
    }

    private ResponseEntity<String> getConversationRuns(long conversationId) {
        return restTemplate.exchange(
                "/api/v1/agent-conversations/" + conversationId + "/runs",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers(ownerCookie)),
                String.class);
    }

    private ResponseEntity<String> getTrace(String runId) {
        return restTemplate.exchange(
                "/api/v1/agent-runs/" + runId + "/trace",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers(ownerCookie)),
                String.class);
    }

    private ResponseEntity<String> createRun(String message, String clientRequestId) {
        return createRun(message, clientRequestId, null);
    }

    private ResponseEntity<String> createRun(String message, String clientRequestId, String mediumToolConfirmation) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("organizationId", organizationId);
        body.put("skill", "PRODUCT");
        body.put("message", message);
        body.put("clientRequestId", clientRequestId);
        if (mediumToolConfirmation != null) {
            body.put("mediumToolConfirmation", mediumToolConfirmation);
        }
        return csrf().post("/api/v1/agent-runs", body, ownerCookie, String.class);
    }

    private JsonNode awaitTerminal(String runId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        JsonNode snapshot;
        do {
            ResponseEntity<String> response = getRun(runId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            snapshot = data(response);
            if (snapshot.get("terminal").asBoolean()) {
                return snapshot;
            }
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Fake dispatcher did not finish run before deadline");
    }

    private ResponseEntity<String> getRun(String runId) {
        return restTemplate.exchange(
                "/api/v1/agent-runs/" + runId,
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers(ownerCookie)),
                String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        JsonNode body = objectMapper.readTree(response.getBody());
        return body.has("data") && body.path("code").asInt(-1) == 0 ? body.get("data") : body;
    }

    private String loginOwner() {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", "owner@example.com", "password", "correct-horse-42"),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return response.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE);
    }

    private org.springframework.http.HttpHeaders headers(String cookie) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(org.springframework.http.HttpHeaders.COOKIE, cookie);
        return headers;
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }
}
