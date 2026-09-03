package ai.forge.server.workitem;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.workitem.application.RequirementTransitionService;
import ai.forge.server.workitem.domain.WorkflowAction;
import ai.forge.server.workitem.domain.WorkflowGuardFailedException;
import ai.forge.server.workitem.domain.IdempotencyConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.dao.DataIntegrityViolationException;

class RequirementTransitionIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 Session、CSRF 和 Controller 调用 Transition API。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析稳定错误、转换结果和活动时间线响应。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 仅搭建本轮尚无编辑 API 的 Requirement Details 事实并验证事务结果。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /* 并发测试通过真实 Spring 事务代理执行相同业务入口。 */
    @Autowired
    private RequirementTransitionService transitionService;

    private String ownerCookie;
    private long ownerId;
    private long workspaceId;
    private long projectId;
    private long requirementId;

    @BeforeEach
    void initializeRequirement() throws Exception {
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "work_item_events", "review_records", "requirement_details", "work_items", "project_item_sequences",
                "project_members", "projects", "audit_logs", "member_roles", "workspace_members", "workspaces",
                "organizations", "users")) {
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
                        "workspaceName", "Engineering",
                        "workspaceSlug", "engineering"),
                null,
                String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ownerCookie = login();
        ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'owner@example.com'", Long.class);
        workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'engineering'", Long.class);
        ResponseEntity<String> project = csrf().post(
                "/api/v1/projects",
                Map.of("workspaceId", workspaceId, "key", "FORGE", "name", "ForgeAI", "description", "会话 12"),
                ownerCookie,
                String.class);
        projectId = objectMapper.readTree(project.getBody()).get("id").asLong();
        ResponseEntity<String> requirement = csrf().post(
                "/api/v1/work-items",
                Map.of(
                        "workspaceId", workspaceId,
                        "projectId", projectId,
                        "type", "REQUIREMENT",
                        "title", "Requirement workflow",
                        "description", "fixed actions",
                        "priority", "HIGH"),
                ownerCookie,
                String.class);
        requirementId = objectMapper.readTree(requirement.getBody()).get("id").asLong();
    }

    @Test
    void missingMaterialsReturnMachineReadableGuardFailureWithoutAnyWrite() throws Exception {
        ResponseEntity<String> response = transition(
                WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "submit-missing", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.get("code").asText()).isEqualTo("WORKFLOW_GUARD_FAILED");
        assertThat(error.at("/details/missing")).extracting(JsonNode::asText)
                .containsExactly("goal", "inScope", "acceptanceCriteria");
        assertUnchangedDraft();
    }

    @Test
    void submitAndRejectWriteStateReviewAndEventsInTheSameTransactions() throws Exception {
        completeMaterials();
        JsonNode submitted = objectMapper.readTree(transition(
                        WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "submit-1", null)
                .getBody());
        JsonNode rejected = objectMapper.readTree(transition(
                        WorkflowAction.REJECT_PRODUCT_REVIEW, 1, "reject-1", "  clarify scope  ")
                .getBody());

        assertThat(submitted.get("status").asText()).isEqualTo("PRODUCT_REVIEW");
        assertThat(submitted.get("version").asLong()).isEqualTo(1);
        assertThat(rejected.get("status").asText()).isEqualTo("DRAFT");
        assertThat(rejected.get("version").asLong()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CONCAT(status, ':', version) FROM work_items WHERE id = ?",
                        String.class,
                        requirementId))
                .isEqualTo("DRAFT:2");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT status FROM review_records WHERE work_item_id = ? ORDER BY id",
                        String.class,
                        requirementId))
                .containsExactly("SUBMITTED", "REJECTED");

        JsonNode timeline = objectMapper.readTree(get("/api/v1/work-items/" + requirementId + "/events?workspaceId="
                        + workspaceId + "&projectId=" + projectId)
                .getBody());
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).get("action").asText()).isEqualTo("SUBMIT_PRODUCT_REVIEW");
        assertThat(timeline.get(1).get("reason").asText()).isEqualTo("clarify scope");
        assertThat(timeline.get(0).get("id").asLong()).isLessThan(timeline.get(1).get("id").asLong());
    }

    @Test
    void missingRejectReasonDoesNotIncrementVersionOrAppendHistory() throws Exception {
        completeMaterials();
        transition(WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "submit-before-reject", null);

        ResponseEntity<String> response = transition(
                WorkflowAction.REJECT_PRODUCT_REVIEW, 1, "reject-without-reason", "  ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(response.getBody()).at("/details/missing/0").asText()).isEqualTo("reason");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CONCAT(status, ':', version) FROM work_items WHERE id = ?",
                        String.class,
                        requirementId))
                .isEqualTo("PRODUCT_REVIEW:1");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_item_events WHERE work_item_id = ?", Integer.class, requirementId))
                .isOne();
    }

    @Test
    void retryWithTheSameKeyReturnsTheOriginalResultWithoutDuplicateWrites() throws Exception {
        completeMaterials();
        ResponseEntity<String> first = transition(
                WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "stable-retry", null);
        ResponseEntity<String> retry = transition(
                WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "stable-retry", null);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(retry.getBody())).isEqualTo(objectMapper.readTree(first.getBody()));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_item_events WHERE work_item_id = ?", Integer.class, requirementId))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT version FROM work_items WHERE id = ?", Long.class, requirementId))
                .isOne();
    }

    @Test
    void reusingAnIdempotencyKeyForAnotherActionIsRejected() {
        completeMaterials();
        transitionService.transition(
                ownerId,
                workspaceId,
                projectId,
                requirementId,
                WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                0,
                "one-purpose-only",
                null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transitionService.transition(
                        ownerId,
                        workspaceId,
                        projectId,
                        requirementId,
                        WorkflowAction.REJECT_PRODUCT_REVIEW,
                        1,
                        "one-purpose-only",
                        "reject"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void failedReviewOrEventWriteRollsBackTheStatusAndVersion() {
        completeMaterials();
        transitionService.transition(
                ownerId,
                workspaceId,
                projectId,
                requirementId,
                WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                0,
                "submit-before-rollback",
                null);
        String oversizedReason = "x".repeat(1001);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transitionService.transition(
                        ownerId,
                        workspaceId,
                        projectId,
                        requirementId,
                        WorkflowAction.REJECT_PRODUCT_REVIEW,
                        1,
                        "force-write-failure",
                        oversizedReason))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CONCAT(status, ':', version) FROM work_items WHERE id = ?",
                        String.class,
                        requirementId))
                .isEqualTo("PRODUCT_REVIEW:1");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_item_events WHERE work_item_id = ?", Integer.class, requirementId))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM review_records WHERE work_item_id = ?", Integer.class, requirementId))
                .isOne();
    }

    @Test
    void concurrentTransitionsWithOneVersionHaveExactlyOneWinner() throws Exception {
        completeMaterials();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> {
                        transitionAfter(start, "race-a");
                        return null;
                    }),
                    executor.submit(() -> {
                        transitionAfter(start, "race-b");
                        return null;
                    }));
            start.countDown();
            int successes = 0;
            int conflicts = 0;
            for (Future<?> future : futures) {
                try {
                    future.get();
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(VersionConflictException.class);
                    conflicts++;
                }
            }
            assertThat(successes).isOne();
            assertThat(conflicts).isOne();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM work_item_events WHERE work_item_id = ?", Integer.class, requirementId))
                    .isOne();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM review_records WHERE work_item_id = ?", Integer.class, requirementId))
                    .isOne();
        }
    }

    @Test
    void eventTimelineCannotCrossTheRequestedScope() {
        ResponseEntity<String> response = get("/api/v1/work-items/" + requirementId + "/events?workspaceId="
                + workspaceId + "&projectId=" + (projectId + 999));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void transitionAfter(CountDownLatch start, String idempotencyKey) throws InterruptedException {
        start.await();
        transitionService.transition(
                ownerId,
                workspaceId,
                projectId,
                requirementId,
                WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                0,
                idempotencyKey,
                null);
    }

    private void completeMaterials() {
        jdbcTemplate.update(
                "INSERT INTO requirement_details (work_item_id, workspace_id, goal, in_scope, out_of_scope, "
                        + "acceptance_criteria_json, business_value, updated_at, version) VALUES "
                        + "(?, ?, 'Ship safely', 'Transition API', '', JSON_ARRAY('State changes'), '', UTC_TIMESTAMP(6), 0)",
                requirementId,
                workspaceId);
    }

    private ResponseEntity<String> transition(
            WorkflowAction action, long expectedVersion, String idempotencyKey, String reason) {
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("action", action.name());
        body.put("expectedVersion", expectedVersion);
        body.put("idempotencyKey", idempotencyKey);
        if (reason != null) {
            body.put("reason", reason);
        }
        return csrf().post(
                "/api/v1/work-items/" + requirementId + "/transitions?workspaceId=" + workspaceId
                        + "&projectId=" + projectId,
                body,
                ownerCookie,
                String.class);
    }

    private void assertUnchangedDraft() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CONCAT(status, ':', version) FROM work_items WHERE id = ?",
                        String.class,
                        requirementId))
                .isEqualTo("DRAFT:0");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_item_events WHERE work_item_id = ?", Integer.class, requirementId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM review_records WHERE work_item_id = ?", Integer.class, requirementId))
                .isZero();
    }

    private String login() {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", "owner@example.com", "password", "correct-horse-42"),
                null,
                String.class);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, ownerCookie);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }
}
