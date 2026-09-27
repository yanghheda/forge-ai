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

    private static final String GENERATED_REQUIREMENT_DESCRIPTION =
            "用于验证 Agent 创建需求契约的完整业务描述。";

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
        /* documents 与 document_versions 存在相互外键，先解除当前版本指针。 */
        jdbcTemplate.update("UPDATE documents SET current_version_id = NULL");
        /* work_items 存在 parent 自引用外键，先解除指针再清空。 */
        jdbcTemplate.update("UPDATE work_items SET parent_id = NULL");
        for (String table : List.of(
                "outbox_events", "agent_tool_calls", "approvals", "agent_events", "agent_steps", "agent_runs", "comments",
                "document_versions", "documents",
                "test_results", "test_runs", "test_cases",
                "work_item_relations", "work_item_labels", "organization_policies", "work_item_events",
                "review_records", "requirement_participants", "requirement_details", "work_items", "organization_item_sequences",
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
                        "organizationSlug", "forge"),
                null,
                String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'owner@example.com'", Long.class);
        organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1", Long.class);
        /* 模块写权限已按领域收敛，Owner 不再持有任何模块写权限（ADR-026）；
           内部 Tool 链需要在同一身份下覆盖产品/设计/研发/测试写入，因此叠加四个领域管理员角色，
           需求参与者与执行人仍由各用例单独配置。 */
        for (String role : List.of("PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN")) {
            jdbcTemplate.update("INSERT IGNORE INTO member_roles(organization_member_id,role_id,created_at) "
                    + "SELECT om.id,r.id,UTC_TIMESTAMP(6) FROM organization_members om JOIN roles r ON r.code=? "
                    + "WHERE om.organization_id=? AND om.user_id=?", role, organizationId, ownerId);
        }
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
                Map.of("title", "无权限需求", "description", GENERATED_REQUIREMENT_DESCRIPTION));

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
                Map.of("title", "撤权后不得创建", "description", GENERATED_REQUIREMENT_DESCRIPTION));

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
                "create_requirement", runToken(runId), "call-1",
                Map.of("title", "被拒绝的需求", "description", GENERATED_REQUIREMENT_DESCRIPTION));

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
                "create_requirement", runToken(runId), "call-1",
                Map.of("title", "待确认需求", "description", GENERATED_REQUIREMENT_DESCRIPTION));

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
    void mediumRequesterCanConfirmAgentExecution() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ASK");
        JsonNode waiting = objectMapper.readTree(executeTool(
                "create_requirement", runToken(runId), "call-1",
                Map.of("title", "产品确认的需求", "description", GENERATED_REQUIREMENT_DESCRIPTION)).getBody());

        ResponseEntity<String> response = csrf().post(
                "/api/v1/approvals/" + waiting.get("approvalId").asText() + ":decide",
                Map.of("decision", "APPROVE", "expectedVersion", 0),
                loginOwner(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody()).get("status").asText())
                .isEqualTo("APPROVED");
    }

    @Test
    void approvedCallResumesWithFrozenInputAndReplaysOnlyOnce() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ASK");
        Map<String, Object> arguments = Map.of(
                "title", "审批后的需求",
                "description", GENERATED_REQUIREMENT_DESCRIPTION);
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
                "create_requirement", runToken(runId), "call-1",
                Map.of("title", "冻结需求", "description", GENERATED_REQUIREMENT_DESCRIPTION)).getBody());
        jdbcTemplate.update("UPDATE approvals SET status = 'APPROVED' WHERE id = ?",
                waiting.get("approvalId").asText());
        jdbcTemplate.update("UPDATE agent_runs SET status = 'RUNNING' WHERE id = ?", runId);

        ResponseEntity<String> changed = executeTool(
                "create_requirement", runToken(runId), "call-1",
                Map.of("title", "篡改需求", "description", GENERATED_REQUIREMENT_DESCRIPTION));

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changed.getBody()).contains("APPROVAL_INPUT_CHANGED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_items", Long.class)).isZero();
    }

    @Test
    void approvedCallRejectsChangedResourceVersion() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "待设计需求", null, WorkItemPriority.MEDIUM, null, null);
        String runId = insertRunningRun("UX", "ASK");
        Map<String, Object> arguments = Map.of("requirementId", requirement.id(), "title", "UX 任务", "assigneeUserId", ownerId);
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
                Map.of(
                        "title", "首个需求",
                        "description", GENERATED_REQUIREMENT_DESCRIPTION,
                        "priority", "HIGH"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode firstExecution = objectMapper.readTree(first.getBody());
        assertThat(firstExecution.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(firstExecution.get("replayed").asBoolean()).isFalse();
        long createdItemId = firstExecution.get("result").get("id").asLong();
        assertThat(firstExecution.get("result").get("itemKey").asText()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT description FROM work_items WHERE id = ?",
                        String.class,
                        createdItemId))
                .isEqualTo(GENERATED_REQUIREMENT_DESCRIPTION);

        ResponseEntity<String> replayed = executeTool(
                "create_requirement",
                runToken(runId),
                "call-1",
                Map.of(
                        "title", "首个需求",
                        "description", GENERATED_REQUIREMENT_DESCRIPTION,
                        "priority", "HIGH"));
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
                Map.of("title", "第二个需求", "description", GENERATED_REQUIREMENT_DESCRIPTION));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(second.getBody()).get("replayed").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_items WHERE type = 'REQUIREMENT'", Long.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isEqualTo(2);
    }

    @Test
    void forcedConfirmationToolPausesEvenWhenRunPolicyAllows() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "测试结论需求", "用于验证执行结论的强制确认",
                WorkItemPriority.HIGH, null, null);
        String runId = insertRunningRun(ownerId, "QA", "ALLOW", requirement.id());

        /* 契约声明 requires_confirmation，因此即使 Run 策略为 ALLOW 也不得自动落库执行结论（ADR-023 §3）。 */
        ResponseEntity<String> response = executeTool(
                "record_test_result", runToken(runId), "call-1",
                Map.of("runId", 1, "resultId", 1, "status", "PASS", "expectedVersion", 0));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode execution = objectMapper.readTree(response.getBody());
        assertThat(execution.get("status").asText()).isEqualTo("WAITING_APPROVAL");
        assertThat(execution.get("approvalId").asText()).hasSize(26);
        assertThat(execution.get("result").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM agent_runs WHERE id = ?", String.class, runId))
                .isEqualTo("WAITING_APPROVAL");
        Map<String, Object> approval = jdbcTemplate.queryForMap(
                "SELECT risk_level, status FROM approvals WHERE run_id = ?", runId);
        assertThat(approval.get("risk_level")).isEqualTo("MEDIUM");
        assertThat(approval.get("status")).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isEqualTo(1);
    }

    @Test
    void stageBoundRunsAdvanceProductStageAndRejectLeaderApprovalsOrCrossScopeActions() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "Agent 推进需求", "已具备评审材料",
                WorkItemPriority.HIGH, null, null);
        var anotherRequirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "不可越权推进", null, WorkItemPriority.MEDIUM, null, null);
        jdbcTemplate.update(
                "INSERT INTO documents (organization_id, work_item_id, type, title, status, visibility, "
                        + "created_by, created_at, updated_at, version) VALUES "
                        + "(?, ?, 'PRD', 'Agent PRD', 'PUBLISHED', 'ORGANIZATION', ?, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                organizationId, requirement.id(), ownerId);
        ai.forge.server.workitem.support.DeliveryTestFixtures.assignAllDomains(jdbcTemplate, organizationId, requirement.id(), ownerId);
        String productRunId = insertRunningRun(ownerId, "PRODUCT", "ALLOW", requirement.id());

        JsonNode submitted = objectMapper.readTree(executeTool(
                "advance_requirement", runToken(productRunId), "product-submit",
                Map.of("requirementId", requirement.id(), "action", "SUBMIT_PRODUCT_REVIEW",
                        "expectedVersion", 0)).getBody());
        ResponseEntity<String> wrongRole = executeTool(
                "advance_requirement", runToken(productRunId), "product-ux-action",
                Map.of("requirementId", requirement.id(), "action", "SUBMIT_UX_REVIEW",
                        "expectedVersion", 1));
        ResponseEntity<String> wrongRequirement = executeTool(
                "advance_requirement", runToken(productRunId), "product-other-requirement",
                Map.of("requirementId", anotherRequirement.id(), "action", "SUBMIT_PRODUCT_REVIEW",
                        "expectedVersion", 0));

        assertThat(submitted.at("/result/status").asText()).isEqualTo("REQUIREMENT_APPROVAL");
        assertThat(wrongRole.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wrongRole.getBody()).contains("ACTION_NOT_ALLOWED_FOR_SKILL");
        assertThat(wrongRequirement.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wrongRequirement.getBody()).contains("RUN_WORK_ITEM_MISMATCH");

        /* 领导审批动作已从 advance_requirement 契约枚举移除，因此先被 schema 校验拒绝（ADR-023 §1）。 */
        ResponseEntity<String> requirementApprove = executeTool(
                "advance_requirement", runToken(productRunId), "requirement-approve",
                Map.of("requirementId", requirement.id(), "action", "APPROVE_REQUIREMENT",
                        "expectedVersion", 1));
        ResponseEntity<String> productReviewApprove = executeTool(
                "advance_requirement", runToken(productRunId), "product-review-approve",
                Map.of("requirementId", requirement.id(), "action", "APPROVE_PRODUCT_REVIEW",
                        "expectedVersion", 1));

        assertThat(requirementApprove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(requirementApprove.getBody()).contains("SCHEMA_INVALID");
        assertThat(productReviewApprove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(productReviewApprove.getBody()).contains("SCHEMA_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM work_items WHERE id = ?", String.class, requirement.id()))
                .isEqualTo("REQUIREMENT_APPROVAL");

        String uxRunId = insertRunningRun(ownerId, "UX", "ALLOW", requirement.id());
        for (Map.Entry<String, String> leaderAction : Map.of(
                        "ux-requirement-approve", "APPROVE_REQUIREMENT",
                        "ux-product-approve", "APPROVE_PRODUCT_REVIEW",
                        "ux-review-approve", "APPROVE_UX_REVIEW",
                        "ux-accept", "ACCEPT_REQUIREMENT")
                .entrySet()) {
            ResponseEntity<String> rejected = executeTool(
                    "advance_requirement", runToken(uxRunId), leaderAction.getKey(),
                    Map.of("requirementId", requirement.id(), "action", leaderAction.getValue(),
                            "expectedVersion", 1));
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(rejected.getBody()).contains("SCHEMA_INVALID");
        }
    }

    @Test
    void readToolsExposeScopedListsAndRejectForeignRequirementScope() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "读取范围需求", "用于验证读取工具的公司范围",
                WorkItemPriority.HIGH, null, null);
        var foreign = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "其他范围需求", "不应被当前 Run 读取",
                WorkItemPriority.MEDIUM, null, null);
        ai.forge.server.workitem.support.DeliveryTestFixtures.assignAllDomains(
                jdbcTemplate, organizationId, requirement.id(), ownerId);
        /* 任务级动作由五态任务生命周期决定，与需求级流转分开（ADR-023 §5）。 */
        long taskId = insertTask(requirement.id(), "DEV_TASK", "读取工具任务", "IN_PROGRESS");
        String runId = insertRunningRun(ownerId, "DEVELOPER", "ALLOW", requirement.id());

        JsonNode requirements = objectMapper.readTree(executeTool(
                "list_requirements", runToken(runId), "call-1", Map.of("query", "读取范围")).getBody());
        assertThat(requirements.at("/result/total").asLong()).isEqualTo(1);
        assertThat(requirements.at("/result/items/0/id").asLong()).isEqualTo(requirement.id());

        JsonNode tasks = objectMapper.readTree(executeTool(
                "list_tasks", runToken(runId), "call-2", Map.of()).getBody());
        assertThat(tasks.at("/result/requirementId").asLong()).isEqualTo(requirement.id());
        assertThat(tasks.at("/result/items/0/id").asLong()).isEqualTo(taskId);

        JsonNode task = objectMapper.readTree(executeTool(
                "get_work_item", runToken(runId), "call-3", Map.of("workItemId", taskId)).getBody());
        assertThat(task.at("/result/type").asText()).isEqualTo("DEV_TASK");
        assertThat(task.at("/result/parentRequirementId").asLong()).isEqualTo(requirement.id());
        assertThat(task.at("/result/availableActions").toString()).contains("SUBMIT_REVIEW");
        /* 根需求尚未进入开发阶段，guardHints 必须显式暴露缺失的阶段前置条件。 */
        assertThat(task.at("/result/guardHints").toString()).contains("activeTaskStage");

        JsonNode members = objectMapper.readTree(executeTool(
                "list_members", runToken(runId), "call-4", Map.of()).getBody());
        assertThat(members.at("/result/items/0/userId").asLong()).isEqualTo(ownerId);

        ResponseEntity<String> foreignScope = executeTool(
                "list_bugs", runToken(runId), "call-5", Map.of("requirementId", foreign.id()));
        assertThat(foreignScope.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(foreignScope.getBody()).contains("RUN_WORK_ITEM_MISMATCH");
    }

    @Test
    void idempotencyKeyCannotBeReusedWithDifferentArguments() throws Exception {
        String runId = insertRunningRun("PRODUCT", "ALLOW");

        ResponseEntity<String> first = executeTool(
                "create_requirement", runToken(runId), "call-1",
                Map.of("title", "原始需求", "description", GENERATED_REQUIREMENT_DESCRIPTION));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> changed = executeTool(
                "create_requirement", runToken(runId), "call-1",
                Map.of("title", "篡改后的需求", "description", GENERATED_REQUIREMENT_DESCRIPTION));

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
        ai.forge.server.workitem.support.DeliveryTestFixtures.assignAllDomains(jdbcTemplate, organizationId, requirement.id(), ownerId);
        jdbcTemplate.update("UPDATE work_items SET status='READY_FOR_UX' WHERE id=?",requirement.id());
        String runId = insertRunningRun("UX", "ALLOW");

        ResponseEntity<String> response = executeTool(
                "create_ux_task",
                runToken(runId),
                "call-1",
                Map.of("requirementId", requirement.id(), "title", "首页体验任务", "assigneeUserId", ownerId));

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

    @Test
    void qaRunCreatesQaTaskForAssignedExecutor() throws Exception {
        var requirement = workItemCommandService.create(
                ownerId,
                organizationId,
                WorkItemType.REQUIREMENT,
                "QA 父需求",
                null,
                WorkItemPriority.MEDIUM,
                null,
                null);
        ai.forge.server.workitem.support.DeliveryTestFixtures.assignAllDomains(jdbcTemplate, organizationId, requirement.id(), ownerId);
        jdbcTemplate.update("UPDATE work_items SET status='READY_FOR_QA' WHERE id=?", requirement.id());
        String runId = insertRunningRun("QA", "ALLOW");

        ResponseEntity<String> response = executeTool(
                "create_qa_task",
                runToken(runId),
                "call-1",
                Map.of("requirementId", requirement.id(), "title", "回归测试任务", "assigneeUserId", ownerId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode execution = objectMapper.readTree(response.getBody());
        assertThat(execution.get("status").asText()).isEqualTo("SUCCEEDED");
        long qaTaskId = execution.get("result").get("id").asLong();
        assertThat(execution.get("result").get("parentId").asLong()).isEqualTo(requirement.id());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT type FROM work_items WHERE id = ?", String.class, qaTaskId))
                .isEqualTo("QA_TASK");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT parent_id FROM work_items WHERE id = ?", Long.class, qaTaskId))
                .isEqualTo(requirement.id());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT assignee_user_id FROM work_items WHERE id = ?", Long.class, qaTaskId))
                .isEqualTo(ownerId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_tool_calls", Long.class))
                .isEqualTo(1);
    }

    @Test
    void productRunCreatesPrdWithGeneratedInitialVersion() throws Exception {
        var requirement = workItemCommandService.create(
                ownerId,
                organizationId,
                WorkItemType.REQUIREMENT,
                "真实正文需求",
                "PRD 页面必须展示 Agent 生成的正文",
                WorkItemPriority.HIGH,
                null,
                null);
        String runId = insertRunningRun(ownerId, "PRODUCT", "ALLOW", requirement.id());
        String generatedMarkdown = """
                # 1 背景与目标

                PRD 页面展示 Agent 生成的真实正文，而不是固定模板。

                ## 2 验收标准

                - 新建后立即产生版本 1
                - 页面刷新后正文保持不变
                """;

        ResponseEntity<String> response = executeTool(
                "create_prd_document",
                runToken(runId),
                "prd-1",
                Map.of(
                        "requirementId", requirement.id(),
                        "title", "真实正文 PRD",
                        "contentMarkdown", generatedMarkdown));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode execution = objectMapper.readTree(response.getBody());
        assertThat(execution.at("/result/version").asLong()).isEqualTo(1);
        assertThat(execution.at("/result/currentVersionId").asLong()).isPositive();
        assertThat(execution.at("/result/currentVersionNo").asLong()).isEqualTo(1);
        Map<String, Object> storedVersion = jdbcTemplate.queryForMap(
                "SELECT version_no, plain_text, content FROM document_versions WHERE document_id = ?",
                execution.at("/result/id").asLong());
        assertThat(((Number) storedVersion.get("version_no")).longValue()).isEqualTo(1);
        assertThat(storedVersion.get("plain_text").toString())
                .contains("PRD 页面展示 Agent 生成的真实正文", "页面刷新后正文保持不变");
        assertThat(objectMapper.readTree(storedVersion.get("content").toString()).path("type").asText())
                .isEqualTo("doc");
    }

    @Test
    void productPrdCreationRejectsMissingGeneratedContent() {
        var requirement = workItemCommandService.create(
                ownerId,
                organizationId,
                WorkItemType.REQUIREMENT,
                "缺少正文需求",
                null,
                WorkItemPriority.MEDIUM,
                null,
                null);
        String runId = insertRunningRun(ownerId, "PRODUCT", "ALLOW", requirement.id());

        ResponseEntity<String> response = executeTool(
                "create_prd_document",
                runToken(runId),
                "prd-without-content",
                Map.of("requirementId", requirement.id(), "title", "空 PRD"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("SCHEMA_INVALID");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class)).isZero();
    }

    @Test
    void productRunFillsLegacyEmptyPrdInsteadOfCreatingDuplicateDocument() throws Exception {
        var requirement = workItemCommandService.create(
                ownerId,
                organizationId,
                WorkItemType.REQUIREMENT,
                "历史空 PRD 需求",
                null,
                WorkItemPriority.MEDIUM,
                null,
                null);
        jdbcTemplate.update(
                "INSERT INTO documents (organization_id, work_item_id, type, title, status, visibility, "
                        + "current_version_id, created_by, created_at, updated_at, deleted_at, version) "
                        + "VALUES (?, ?, 'PRD', '历史空 PRD', 'DRAFT', 'ORGANIZATION', NULL, ?, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)",
                organizationId,
                requirement.id(),
                ownerId);
        long legacyDocumentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String runId = insertRunningRun(ownerId, "PRODUCT", "ALLOW", requirement.id());

        JsonNode execution = objectMapper.readTree(executeTool(
                "create_prd_document",
                runToken(runId),
                "fill-empty-prd",
                Map.of(
                        "requirementId", requirement.id(),
                        "title", "不应重复创建",
                        "contentMarkdown", "# 背景与目标\n\n修复历史版本 0 的空 PRD 文档。"))
                .getBody());

        assertThat(execution.at("/result/id").asLong()).isEqualTo(legacyDocumentId);
        assertThat(execution.at("/result/currentVersionNo").asLong()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE work_item_id=? AND type='PRD'",
                Long.class,
                requirement.id())).isEqualTo(1);
    }

    /* publish_document 是需求评审的准入前提：未发布 PRD 时评审被 Guard 拒绝，发布后即可推进。 */
    @Test
    void productRunPublishesPrdAndUnlocksRequirementReview() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "评审材料需求", "需要已发布 PRD 才能进入产品评审",
                WorkItemPriority.HIGH, null, null);
        ai.forge.server.workitem.support.DeliveryTestFixtures.assignAllDomains(
                jdbcTemplate, organizationId, requirement.id(), ownerId);
        String runId = insertRunningRun(ownerId, "PRODUCT", "ALLOW", requirement.id());

        JsonNode created = objectMapper.readTree(executeTool(
                "create_prd_document", runToken(runId), "prd-1",
                Map.of("requirementId", requirement.id(), "title", "评审用 PRD",
                        "contentMarkdown", "# 背景与目标\n\nAgent 生成的 PRD 正文，用于解锁产品评审。")).getBody());
        long documentId = created.at("/result/id").asLong();
        assertThat(created.at("/result/status").asText()).isEqualTo("DRAFT");

        ResponseEntity<String> beforePublish = executeTool(
                "advance_requirement", runToken(runId), "submit-before-publish",
                Map.of("requirementId", requirement.id(), "action", "SUBMIT_PRODUCT_REVIEW",
                        "expectedVersion", 0));
        assertThat(beforePublish.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(beforePublish.getBody()).contains("WORKFLOW_GUARD_FAILED");

        JsonNode published = objectMapper.readTree(executeTool(
                "publish_document", runToken(runId), "publish-1",
                Map.of("documentId", documentId, "expectedVersion", created.at("/result/version").asLong())).getBody());
        assertThat(published.at("/result/status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.at("/result/documentType").asText()).isEqualTo("PRD");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM documents WHERE id = ?", String.class, documentId))
                .isEqualTo("PUBLISHED");

        JsonNode submitted = objectMapper.readTree(executeTool(
                "advance_requirement", runToken(runId), "submit-after-publish",
                Map.of("requirementId", requirement.id(), "action", "SUBMIT_PRODUCT_REVIEW",
                        "expectedVersion", 0)).getBody());
        assertThat(submitted.at("/result/status").asText()).isEqualTo("REQUIREMENT_APPROVAL");
    }

    /* 发布只能作用于 Run 绑定需求下的文档，越界文档一律拒绝。 */
    @Test
    void publishDocumentRejectsDocumentOutsideRunRequirement() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "范围内需求", null, WorkItemPriority.MEDIUM, null, null);
        var foreign = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "范围外需求", null, WorkItemPriority.MEDIUM, null, null);
        jdbcTemplate.update(
                "INSERT INTO documents (organization_id, work_item_id, type, title, status, visibility, "
                        + "created_by, created_at, updated_at, version) VALUES "
                        + "(?, ?, 'PRD', '范围外 PRD', 'DRAFT', 'ORGANIZATION', ?, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                organizationId, foreign.id(), ownerId);
        long foreignDocumentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String runId = insertRunningRun(ownerId, "PRODUCT", "ALLOW", requirement.id());

        ResponseEntity<String> rejected = executeTool(
                "publish_document", runToken(runId), "publish-foreign",
                Map.of("documentId", foreignDocumentId, "expectedVersion", 0));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejected.getBody()).contains("RUN_WORK_ITEM_MISMATCH");
    }

    /* 需求详情按字段合并写入，局部更新不得清空既有材料。 */
    @Test
    void productRunUpdatesRequirementDetailsWithPartialMerge() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "详情需求", "用于验证详情材料合并",
                WorkItemPriority.MEDIUM, null, null);
        ai.forge.server.workitem.support.DeliveryTestFixtures.assignAllDomains(
                jdbcTemplate, organizationId, requirement.id(), ownerId);
        String runId = insertRunningRun(ownerId, "PRODUCT", "ALLOW", requirement.id());

        JsonNode first = objectMapper.readTree(executeTool(
                "update_requirement_details", runToken(runId), "details-1",
                Map.of("requirementId", requirement.id(), "goal", "降低下单失败率",
                        "inScope", "结算页", "acceptanceCriteria", List.of("失败率低于 1%"),
                        "expectedVersion", 0)).getBody());
        assertThat(first.at("/result/goal").asText()).isEqualTo("降低下单失败率");
        assertThat(first.at("/result/version").asLong()).isEqualTo(1);

        JsonNode second = objectMapper.readTree(executeTool(
                "update_requirement_details", runToken(runId), "details-2",
                Map.of("requirementId", requirement.id(), "businessValue", "提升转化",
                        "expectedVersion", 1)).getBody());
        assertThat(second.at("/result/goal").asText()).isEqualTo("降低下单失败率");
        assertThat(second.at("/result/inScope").asText()).isEqualTo("结算页");
        assertThat(second.at("/result/acceptanceCriteria/0").asText()).isEqualTo("失败率低于 1%");
        assertThat(second.at("/result/businessValue").asText()).isEqualTo("提升转化");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT business_value FROM requirement_details WHERE work_item_id = ?",
                        String.class, requirement.id()))
                .isEqualTo("提升转化");
    }

    /* 需求删除不可逆，因此即使 Run 策略为 ALLOW 也先暂停等人工确认，确认后才软删除草稿。 */
    @Test
    void deleteRequirementAlwaysPausesForConfirmationThenSoftDeletesDraft() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "待删除草稿", "仅草稿允许软删除",
                WorkItemPriority.LOW, null, null);
        String runId = insertRunningRun(ownerId, "PRODUCT", "ALLOW", requirement.id());
        Map<String, Object> arguments = Map.of("requirementId", requirement.id(), "expectedVersion", 0);

        JsonNode waiting = objectMapper.readTree(
                executeTool("delete_requirement", runToken(runId), "delete-1", arguments).getBody());
        assertThat(waiting.get("status").asText()).isEqualTo("WAITING_APPROVAL");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_items WHERE id = ? AND deleted_at IS NULL",
                        Long.class, requirement.id()))
                .isEqualTo(1);

        jdbcTemplate.update("UPDATE approvals SET status = 'APPROVED' WHERE id = ?",
                waiting.get("approvalId").asText());
        jdbcTemplate.update("UPDATE agent_runs SET status = 'RUNNING' WHERE id = ?", runId);

        JsonNode executed = objectMapper.readTree(
                executeTool("delete_requirement", runToken(runId), "delete-1", arguments).getBody());
        assertThat(executed.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(executed.at("/result/deleted").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_items WHERE id = ? AND deleted_at IS NULL",
                        Long.class, requirement.id()))
                .isZero();
    }

    /* 重开测试运行会清空固化统计并追加审计记录，属 QA Skill 可达的受控写操作。 */
    @Test
    void qaRunReopensCompletedTestRunWithReason() throws Exception {
        var requirement = workItemCommandService.create(ownerId, organizationId,
                WorkItemType.REQUIREMENT, "重开测试需求", "用于验证重开测试运行",
                WorkItemPriority.HIGH, null, null);
        ai.forge.server.workitem.support.DeliveryTestFixtures.assignAllDomains(
                jdbcTemplate, organizationId, requirement.id(), ownerId);
        jdbcTemplate.update("UPDATE work_items SET status = 'IN_QA' WHERE id = ?", requirement.id());
        jdbcTemplate.update(
                "INSERT INTO test_runs (organization_id, requirement_id, environment, status, started_by, "
                        + "started_at, finished_at, summary_json, version) VALUES "
                        + "(?, ?, 'staging', 'COMPLETED', ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), "
                        + "JSON_OBJECT('total', 1), 3)",
                organizationId, requirement.id(), ownerId);
        long testRunId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String runId = insertRunningRun(ownerId, "QA", "ALLOW", requirement.id());

        JsonNode reopened = objectMapper.readTree(executeTool(
                "reopen_test_run", runToken(runId), "reopen-1",
                Map.of("runId", testRunId, "reason", "发现漏测场景", "expectedVersion", 3)).getBody());

        assertThat(reopened.at("/result/status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM test_runs WHERE id = ?", String.class, testRunId))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT summary_json IS NULL FROM test_runs WHERE id = ?", Boolean.class, testRunId))
                .isTrue();
    }

    /* 插入一条 RUNNING 状态的 Run 事实；绕过 Fake Dispatcher 避免状态竞态。 */
    private String insertRunningRun(String skill, String mediumToolConfirmation) {
        return insertRunningRunForUser(ownerId, skill, mediumToolConfirmation);
    }

    private String insertRunningRunForUser(long userId, String skill, String mediumToolConfirmation) {
        return insertRunningRun(userId, skill, mediumToolConfirmation, null);
    }

    private String insertRunningRun(
            long userId, String skill, String mediumToolConfirmation, Long workItemId) {
        String runId = ("00000000000000000000000000" + System.nanoTime());
        runId = runId.substring(runId.length() - 26);
        jdbcTemplate.update(
                "INSERT INTO agent_runs (id, organization_id, work_item_id, user_id, skill, "
                        + "medium_tool_confirmation, message_redacted, client_request_id, request_hash, status, "
                        + "prompt_version, last_sequence, version, created_at, updated_at) VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, SHA2('x', 256), 'RUNNING', 'fake-runner-v1', 0, 0, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                runId,
                organizationId,
                workItemId,
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

    /* 直接搭建角色 Task 事实，避免在读取工具测试中耦合创建阶段的准入 Guard。 */
    private long insertTask(long requirementId, String type, String title, String status) {
        long itemNumber = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(item_number), 0) + 1 FROM work_items WHERE organization_id = ?",
                Long.class, organizationId);
        jdbcTemplate.update(
                "INSERT INTO work_items (organization_id, item_number, item_key, type, title, description, "
                        + "status, priority, parent_id, assignee_user_id, reporter_user_id, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, ?, '', ?, 'MEDIUM', ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                organizationId, itemNumber, "FORGE-" + itemNumber, type, title, status,
                requirementId, ownerId, ownerId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM work_items WHERE organization_id = ? AND item_number = ?",
                Long.class, organizationId, itemNumber);
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
