package ai.forge.server.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.platform.agent.AgentServiceTokenProvider;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class InternalToolIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 HTTP 与真实 MySQL/Redis/Qdrant 验证内部 Tool 执行链。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析 Tool 执行信封与错误响应。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 搭建 Run 与身份事实并断言持久化副作用。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /* 重置登录限流等共享 Redis 状态，保证测试彼此隔离。 */
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    /* 以服务端身份签发 run-scoped 凭据，模拟 forge-agent 回调。 */
    @Autowired
    private AgentServiceTokenProvider tokenProvider;

    /* 仅用于搭建 Requirement 父项事实。 */
    @Autowired
    private WorkItemCommandService workItemCommandService;

    private long ownerId;
    private long organizationId;


    @BeforeEach
    void initializeCompanyAndOrganization() throws Exception {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        /* work_items 存在 parent 自引用外键，先解除指针再清空。 */
        jdbcTemplate.update("UPDATE work_items SET parent_id = NULL");
        for (String table : List.of(
                "outbox_events", "agent_tool_calls", "approvals", "agent_events", "agent_steps", "agent_runs", "comments",
                "work_item_relations", "work_item_labels", "organization_policies", "work_item_events",
                "review_records", "requirement_details", "work_items", "organization_item_sequences",
                "organization_policies", "audit_logs", "member_roles", "organization_members",                 "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        ResponseEntity<String> initialized = csrf().post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "owner@example.com",
                        "adminDisplayName", "Forge Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge",
                        "logoFileName", "logo.webp",
                        "logoMediaType", "image/webp",
                        "logoBase64", "UklGRgAAAABXRUJQVlA4WAAAAAAAAAAAGwAAGwAA"),
                null,
                String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'owner@example.com'", Long.class);
        organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1", Long.class);
    }

    @Test
    void missingBearerCredentialIsRejected() {
        ResponseEntity<String> response = executeTool("get_organization", null, "call-1", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidBearerCredentialIsRejected() {
        ResponseEntity<String> response = executeTool("get_organization", "not-a-jwt", "call-1", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredRunCredentialIsRejected() {
        String runId = insertRunningRun("PRODUCT", "ASK");
        String expired = tokenProvider.createRunToken(
                Instant.now().minus(Duration.ofMinutes(5)), runId, organizationId);

        ResponseEntity<String> response = executeTool("get_organization", expired, "call-1", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unregisteredToolIsRejected() {
        String runId = insertRunningRun("PRODUCT", "ASK");

        ResponseEntity<String> response = executeTool("no_such_tool", runToken(runId), "call-1", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("TOOL_NOT_FOUND");
    }

    @Test
    void runOutsideServerFactsIsHiddenAsNotFound() {
        String runId = insertRunningRun("PRODUCT", "ASK");
        String foreignToken = tokenProvider.createRunToken(
                Instant.now(), "01234567890123456789012345", organizationId);
        String wrongOrganizationToken = tokenProvider.createRunToken(
                Instant.now(), runId, organizationId + 999);

        assertThat(executeTool("get_organization", foreignToken, "call-1", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> wrongOrganization = executeTool("get_organization", wrongOrganizationToken, "call-1", Map.of());
        assertThat(wrongOrganization.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(wrongOrganization.getBody()).contains("RUN_NOT_FOUND");
    }

    @Test
    void terminalRunIsRejectedAsNotActive() {
        String runId = insertRunningRun("PRODUCT", "ASK");
        jdbcTemplate.update(
                "UPDATE agent_runs SET status = 'SUCCEEDED', finished_at = UTC_TIMESTAMP(6) WHERE id = ?",
                runId);

        ResponseEntity<String> response = executeTool("get_organization", runToken(runId), "call-1", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("RUN_NOT_ACTIVE");
    }

    @Test
    void toolOutsideSkillAllowlistIsRejected() {
        String runId = insertRunningRun("PRODUCT", "ALLOW");

        ResponseEntity<String> response = executeTool(
                "create_ux_task",
                runToken(runId),
                "call-1",
                Map.of("requirementId", 1, "title", "越权 UX Task"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("TOOL_NOT_ALLOWED_FOR_SKILL");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_items", Long.class))
                .isZero();
    }

    @Test
    void schemaViolationIsRejected() {
        String runId = insertRunningRun("PRODUCT", "ASK");

        ResponseEntity<String> response = executeTool("get_work_item", runToken(runId), "call-1", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("SCHEMA_INVALID");
    }

    @Test
    void callerCannotInjectOrganizationScopeArguments() {
        String runId = insertRunningRun("PRODUCT", "ASK");

        ResponseEntity<String> response = executeTool(
                "get_organization",
                runToken(runId),
                "call-1",
                Map.of("organizationId", organizationId + 999));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("SCHEMA_INVALID");
    }

    @Test
    void permissionDeniedIsRejected() {
        long developerId = insertOrganizationMemberWithRole("DEVELOPER");
        String runId = insertRunningRunForUser(developerId, "PRODUCT", "ALLOW");

        ResponseEntity<String> response = executeTool(
                "create_requirement",
                runToken(runId),
                "call-1",
                Map.of("title", "无权限需求"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("PERMISSION_DENIED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_items", Long.class))
                .isZero();
    }

    @Test
    void permissionIsRecheckedAfterRunCreation() {
        long productUserId = insertOrganizationMemberWithRole("PRODUCT");
        String runId = insertRunningRunForUser(productUserId, "PRODUCT", "ALLOW");
        jdbcTemplate.update(
                "DELETE FROM member_roles WHERE organization_member_id = "
                        + "(SELECT id FROM organization_members WHERE organization_id = ? AND user_id = ?)",
                organizationId,
                productUserId);

        ResponseEntity<String> response = executeTool(
                "create_requirement",
                runToken(runId),
                "call-1",
                Map.of("title", "撤权后不得创建"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("PERMISSION_DENIED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_items", Long.class))
                .isZero();
    }

    @Test
    void lowRiskToolExecutesWithoutToolCallRecord() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ASK");

        ResponseEntity<String> response = executeTool("get_organization", runToken(runId), "call-1", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode execution = objectMapper.readTree(response.getBody());
        assertThat(execution.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(execution.get("replayed").asBoolean()).isFalse();
        assertThat(execution.get("result").get("id").asLong()).isEqualTo(organizationId);
        assertThat(execution.get("result").get("name").asText()).isEqualTo("Forge");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isZero();
    }

    @Test
    void mediumDenyPolicyRejectsExecution() throws Exception {
        String runId = insertRunningRun("PRODUCT", "DENY");

        ResponseEntity<String> response = executeTool(
                "create_requirement", runToken(runId), "call-1", Map.of("title", "被拒绝的需求"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode execution = objectMapper.readTree(response.getBody());
        assertThat(execution.get("status").asText()).isEqualTo("REJECTED");
        assertThat(execution.get("errorCode").asText()).isEqualTo("MEDIUM_DENIED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_items", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isZero();
    }

    @Test
    void mediumAskPolicyPersistsFrozenApprovalAndPausesRun() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ASK");

        ResponseEntity<String> response = executeTool(
                "create_requirement", runToken(runId), "call-1", Map.of("title", "待确认需求"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode execution = objectMapper.readTree(response.getBody());
        assertThat(execution.get("status").asText()).isEqualTo("WAITING_APPROVAL");
        assertThat(execution.get("approvalId").asText()).hasSize(26);
        assertThat(execution.get("result").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_items", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM agent_runs WHERE id = ?", String.class, runId))
                .isEqualTo("WAITING_APPROVAL");
        Map<String, Object> approval = jdbcTemplate.queryForMap(
                "SELECT status, argument_hash, frozen_arguments_encrypted, version FROM approvals WHERE run_id = ?",
                runId);
        assertThat(approval.get("status")).isEqualTo("PENDING");
        assertThat(approval.get("argument_hash").toString()).hasSize(64);
        assertThat(approval.get("frozen_arguments_encrypted").toString()).doesNotContain("待确认需求");
        assertThat(((Number) approval.get("version")).longValue()).isZero();
    }

    @Test
    void approvedCallResumesWithFrozenInputAndReplaysOnlyOnce() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ASK");
        Map<String, Object> arguments = Map.of("title", "审批后的需求");
        JsonNode waiting = objectMapper.readTree(
                executeTool("create_requirement", runToken(runId), "call-1", arguments).getBody());
        String approvalId = waiting.get("approvalId").asText();
        jdbcTemplate.update(
                "UPDATE approvals SET status = 'APPROVED', approver_user_id = ?, version = version + 1 WHERE id = ?",
                ownerId,
                approvalId);
        jdbcTemplate.update("UPDATE agent_runs SET status = 'RUNNING' WHERE id = ?", runId);

        JsonNode executed = objectMapper.readTree(
                executeTool("create_requirement", runToken(runId), "call-1", arguments).getBody());
        JsonNode replayed = objectMapper.readTree(
                executeTool("create_requirement", runToken(runId), "call-1", arguments).getBody());

        assertThat(executed.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(replayed.get("replayed").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_items WHERE title = '审批后的需求'", Long.class)).isEqualTo(1);
    }

    @Test
    void approvedCallRejectsChangedArguments() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ASK");
        JsonNode waiting = objectMapper.readTree(executeTool(
                "create_requirement", runToken(runId), "call-1", Map.of("title", "冻结需求")).getBody());
        jdbcTemplate.update("UPDATE approvals SET status = 'APPROVED' WHERE id = ?",
                waiting.get("approvalId").asText());
        jdbcTemplate.update("UPDATE agent_runs SET status = 'RUNNING' WHERE id = ?", runId);

        ResponseEntity<String> changed = executeTool(
                "create_requirement", runToken(runId), "call-1", Map.of("title", "篡改需求"));

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changed.getBody()).contains("APPROVAL_INPUT_CHANGED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_items", Long.class)).isZero();
    }

    @Test
    void approvedCallRejectsChangedResourceVersion() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "待设计需求", null, WorkItemPriority.MEDIUM, null, null);
        String runId = insertRunningRun("UX", "ASK");
        Map<String, Object> arguments = Map.of("requirementId", requirement.id(), "title", "UX 任务");
        JsonNode waiting = objectMapper.readTree(
                executeTool("create_ux_task", runToken(runId), "call-1", arguments).getBody());
        jdbcTemplate.update("UPDATE approvals SET status = 'APPROVED' WHERE id = ?",
                waiting.get("approvalId").asText());
        jdbcTemplate.update("UPDATE work_items SET version = version + 1 WHERE id = ?", requirement.id());
        jdbcTemplate.update("UPDATE agent_runs SET status = 'RUNNING' WHERE id = ?", runId);

        ResponseEntity<String> changed = executeTool(
                "create_ux_task", runToken(runId), "call-1", arguments);

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changed.getBody()).contains("RESOURCE_VERSION_CHANGED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_items WHERE type = 'UX_TASK'", Long.class)).isZero();
    }

    @Test
    void mediumAllowPolicyExecutesRecordsAndReplaysIdempotently() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ALLOW");

        ResponseEntity<String> first = executeTool(
                "create_requirement",
                runToken(runId),
                "call-1",
                Map.of("title", "首个需求", "priority", "HIGH"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode firstExecution = objectMapper.readTree(first.getBody());
        assertThat(firstExecution.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(firstExecution.get("replayed").asBoolean()).isFalse();
        long createdItemId = firstExecution.get("result").get("id").asLong();
        assertThat(firstExecution.get("result").get("itemKey").asText()).isNotBlank();

        ResponseEntity<String> replayed = executeTool(
                "create_requirement",
                runToken(runId),
                "call-1",
                Map.of("title", "首个需求", "priority", "HIGH"));
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode replayExecution = objectMapper.readTree(replayed.getBody());
        assertThat(replayExecution.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(replayExecution.get("replayed").asBoolean()).isTrue();
        assertThat(replayExecution.get("result").get("id").asLong()).isEqualTo(createdItemId);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_items WHERE type = 'REQUIREMENT'", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isEqualTo(1);
        Map<String, Object> toolCall = jdbcTemplate.queryForMap(
                "SELECT tool_name, risk_level, status, idempotency_key FROM agent_tool_calls WHERE run_id = ?",
                runId);
        assertThat(toolCall.get("tool_name")).isEqualTo("create_requirement");
        assertThat(toolCall.get("risk_level")).isEqualTo("MEDIUM");
        assertThat(toolCall.get("status")).isEqualTo("SUCCEEDED");
        assertThat(toolCall.get("idempotency_key")).isEqualTo(runId + ":call-1");

        ResponseEntity<String> second = executeTool(
                "create_requirement",
                runToken(runId),
                "call-2",
                Map.of("title", "第二个需求"));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(second.getBody()).get("replayed").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_items WHERE type = 'REQUIREMENT'", Long.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isEqualTo(2);
    }

    @Test
    void idempotencyKeyCannotBeReusedWithDifferentArguments() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ALLOW");

        ResponseEntity<String> first = executeTool(
                "create_requirement", runToken(runId), "call-1", Map.of("title", "原始需求"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> changed = executeTool(
                "create_requirement", runToken(runId), "call-1", Map.of("title", "篡改后的需求"));

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changed.getBody()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_items WHERE type = 'REQUIREMENT'", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT title FROM work_items WHERE type = 'REQUIREMENT'", String.class))
                .isEqualTo("原始需求");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isEqualTo(1);
    }

    @Test
    void uxRunCreatesUxTaskUnderRequirement() throws Exception {
        var requirement = workItemCommandService.create(
                ownerId,
                organizationId,
                WorkItemType.REQUIREMENT,
                "UX 父需求",
                null,
                WorkItemPriority.MEDIUM,
                null,
                null);
        String runId = insertRunningRun("UX", "ALLOW");

        ResponseEntity<String> response = executeTool(
                "create_ux_task",
                runToken(runId),
                "call-1",
                Map.of("requirementId", requirement.id(), "title", "首页体验任务"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode execution = objectMapper.readTree(response.getBody());
        assertThat(execution.get("status").asText()).isEqualTo("SUCCEEDED");
        long uxTaskId = execution.get("result").get("id").asLong();
        assertThat(execution.get("result").get("parentId").asLong()).isEqualTo(requirement.id());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT type FROM work_items WHERE id = ?", String.class, uxTaskId))
                .isEqualTo("UX_TASK");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT parent_id FROM work_items WHERE id = ?", Long.class, uxTaskId))
                .isEqualTo(requirement.id());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isEqualTo(1);
    }

    /* 插入一条 RUNNING 状态的 Run 事实；绕过 Fake Dispatcher 避免状态竞态。 */
    private String insertRunningRun(String skill, String mediumToolConfirmation) {
        return insertRunningRunForUser(ownerId, skill, mediumToolConfirmation);
    }

    private String insertRunningRunForUser(long userId, String skill, String mediumToolConfirmation) {
        String runId = ("00000000000000000000000000" + System.nanoTime());
        runId = runId.substring(runId.length() - 26);
        jdbcTemplate.update(
                "INSERT INTO agent_runs (id, organization_id, work_item_id, user_id, skill, "
                        + "medium_tool_confirmation, message_redacted, client_request_id, request_hash, status, "
                        + "prompt_version, last_sequence, version, created_at, updated_at) VALUES "
                        + "(?, ?, NULL, ?, ?, ?, ?, ?, SHA2('x', 256), 'RUNNING', 'fake-runner-v1', 0, 0, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                runId,
                organizationId,
                userId,
                skill,
                mediumToolConfirmation,
                "12 chars",
                "client-" + runId);
        return runId;
    }

    private String runToken(String runId) {
        return tokenProvider.createRunToken(Instant.now(), runId, organizationId);
    }

    private ResponseEntity<String> executeTool(
            String toolName, String token, String toolCallId, Map<String, Object> arguments) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("toolCallId", toolCallId);
        body.put("arguments", arguments);
        return restTemplate.exchange(
                "/internal/v1/tools/" + toolName + ":execute",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private long insertOrganizationMemberWithRole(String roleCode) {
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE normalized_email = 'owner@example.com'", String.class);
        jdbcTemplate.update(
                "INSERT INTO users (email, normalized_email, display_name, password_hash, status, "
                        + "failed_login_count, locked_until, last_login_at, created_at, updated_at, version) VALUES "
                        + "('developer@example.com', 'developer@example.com', 'Developer', ?, 'ACTIVE', 0, NULL, NULL, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                passwordHash);
        long developerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'developer@example.com'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, status, joined_at, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                organizationId,
                developerId);
        jdbcTemplate.update(
                "INSERT INTO member_roles (organization_member_id, role_id, created_at) "
                        + "SELECT wm.id, r.id, UTC_TIMESTAMP(6) FROM organization_members wm CROSS JOIN roles r "
                        + "WHERE wm.organization_id = ? AND wm.user_id = ? AND r.code = ?",
                organizationId,
                developerId,
                roleCode);
        return developerId;
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }

    private String loginOwner() {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", "owner@example.com", "password", "correct-horse-42"),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }
}
