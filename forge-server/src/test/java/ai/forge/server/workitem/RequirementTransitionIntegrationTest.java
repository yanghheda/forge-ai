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
    private long organizationId;

    private long requirementId;
    private String ownerEmail;

    @BeforeEach
    void initializeRequirement() throws Exception {
        ownerEmail = "owner-" + java.util.UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        jdbcTemplate.update("UPDATE documents SET current_version_id = NULL");
        jdbcTemplate.update("UPDATE work_items SET parent_id = NULL");
        for (String table : List.of(
                "webhook_deliveries", "pipeline_runs", "source_control_operations", "merge_requests", "branches",
                "git_repositories", "gitlab_connections", "secrets", "outbox_events", "document_versions",
                "documents", "comments", "work_item_relations",
                "work_item_labels", "organization_policies", "work_item_events", "review_records",
                "requirement_details", "work_items", "organization_item_sequences",
                "organization_policies", "audit_logs", "member_roles", "organization_members",                 "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        ResponseEntity<String> initialized = csrf().post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", ownerEmail,
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
        ownerCookie = login();
        ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = ?", Long.class, ownerEmail);
        organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1", Long.class);
        ResponseEntity<String> requirement = csrf().post(
                "/api/v1/work-items",
                Map.of(
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

        JsonNode timeline = objectMapper.readTree(get("/api/v1/work-items/" + requirementId + "/events?organizationId="
                        + organizationId + "&organizationId=" + organizationId)
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
                organizationId,
                requirementId,
                WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                0,
                "one-purpose-only",
                null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transitionService.transition(
                        ownerId,
                        organizationId,
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
                organizationId,
                requirementId,
                WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                0,
                "submit-before-rollback",
                null);
        String oversizedReason = "x".repeat(1001);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transitionService.transition(
                        ownerId,
                        organizationId,
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
    void eventTimelineIgnoresClientSuppliedOrganizationScope() {
        ResponseEntity<String> response = get("/api/v1/work-items/" + requirementId + "/events?organizationId="
                + organizationId + "&organizationId=" + (organizationId + 999));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void productCanCompleteTheManualVerticalSliceAndRefreshServerFacts() throws Exception {
        ResponseEntity<String> details = csrf().put(
                "/api/v1/work-items/" + requirementId + "/details?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of(
                        "goal", "Improve login conversion",
                        "inScope", "Mobile login",
                        "outOfScope", "Social login",
                        "acceptanceCriteria", List.of("User can request a code"),
                        "businessValue", "Reduce churn",
                        "expectedVersion", 0),
                ownerCookie,
                String.class);
        assertThat(details.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(details.getBody()).get("version").asLong()).isOne();

        assertThat(transition(WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "product-submit", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<String> missingPrd = transition(
                WorkflowAction.APPROVE_PRODUCT_REVIEW, 1, "approve-without-prd", null);
        assertThat(missingPrd.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(missingPrd.getBody()).at("/details/missing/0").asText())
                .isEqualTo("publishedPrd");

        JsonNode document = objectMapper.readTree(csrf().post(
                "/api/v1/documents",
                Map.of("workItemId", requirementId,
                        "type", "PRD", "title", "Login PRD"),
                ownerCookie,
                String.class).getBody());
        long documentId = document.get("id").asLong();
        JsonNode saved = objectMapper.readTree(csrf().post(
                "/api/v1/documents/" + documentId + "/versions?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of("expectedVersion", 0, "content", Map.of("type", "doc", "content", List.of(
                        Map.of("type", "paragraph", "content", List.of(
                                Map.of("type", "text", "text", "Goal, scope and acceptance criteria")))))),
                ownerCookie,
                String.class).getBody());
        assertThat(csrf().post(
                        "/api/v1/documents/" + documentId + "/publish?organizationId=" + organizationId
                                + "&organizationId=" + organizationId,
                        Map.of("versionId", saved.get("currentVersionId").asLong(),
                                "expectedVersion", saved.get("version").asLong()),
                        ownerCookie,
                        String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode approved = objectMapper.readTree(transition(
                WorkflowAction.APPROVE_PRODUCT_REVIEW, 1, "product-approve", null).getBody());
        assertThat(approved.get("status").asText()).isEqualTo("UX_IN_PROGRESS");
        JsonNode refreshed = objectMapper.readTree(get("/api/v1/work-items/" + requirementId
                        + "?organizationId=" + organizationId + "&organizationId=" + organizationId).getBody());
        assertThat(refreshed.get("status").asText()).isEqualTo("UX_IN_PROGRESS");
        assertThat(refreshed.get("availableActions").get(0).asText()).isEqualTo("SUBMIT_UX_REVIEW");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT status FROM review_records WHERE work_item_id=? ORDER BY id",
                        String.class, requirementId))
                .containsExactly("SUBMITTED", "APPROVED");
    }

    @Test
    void uxReviewFreezesPublishedSpecAndDoesNotLetUxTaskCompletionBypassRequirementReview() throws Exception {
        completeMaterials();
        transition(WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "ux-product-submit", null);
        publishDocument("PRD", "Login PRD", "Product delivery");
        transition(WorkflowAction.APPROVE_PRODUCT_REVIEW, 1, "ux-product-approve", null);

        long uxTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM work_items WHERE parent_id = ? AND type = 'UX_TASK'", Long.class, requirementId);
        transition(WorkflowAction.START, 0, "ux-task-start", null, uxTaskId);
        transition(WorkflowAction.SUBMIT_REVIEW, 1, "ux-task-submit", null, uxTaskId);
        transition(WorkflowAction.APPROVE, 2, "ux-task-approve", null, uxTaskId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM work_items WHERE id = ?", String.class, requirementId))
                .isEqualTo("UX_IN_PROGRESS");

        ResponseEntity<String> missingSpec = transition(
                WorkflowAction.SUBMIT_UX_REVIEW, 2, "ux-requirement-missing-spec", null);
        assertThat(missingSpec.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(missingSpec.getBody()).at("/details/missing/0").asText())
                .isEqualTo("publishedUxSpec");

        JsonNode spec = publishDocument("UX_SPEC", "Login UX Spec", "User flow and exception states");
        JsonNode submitted = objectMapper.readTree(transition(
                        WorkflowAction.SUBMIT_UX_REVIEW,
                        2,
                        "ux-requirement-submit",
                        null,
                        requirementId,
                        List.of("userFlow", "pageList", "keyInteraction", "exceptionState"))
                .getBody());
        assertThat(submitted.get("status").asText()).isEqualTo("UX_REVIEW");
        JsonNode approved = objectMapper.readTree(transition(
                        WorkflowAction.APPROVE_UX_REVIEW, 3, "ux-requirement-approve", null)
                .getBody());
        assertThat(approved.get("status").asText()).isEqualTo("READY_FOR_DEV");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT JSON_CONTAINS(artifact_version_json, JSON_OBJECT('versionId', CAST(? AS UNSIGNED))) "
                                + "FROM review_records "
                                + "WHERE work_item_id = ? AND review_type = 'UX_REVIEW' AND status = 'APPROVED'",
                        String.class,
                        String.valueOf(spec.get("currentVersionId").asLong()),
                        requirementId))
                .isEqualTo("1");

        JsonNode graph = objectMapper.readTree(get("/api/v1/work-items/" + requirementId
                + "/delivery-graph?organizationId=" + organizationId + "&organizationId=" + organizationId).getBody());
        assertThat(graph.get("nodes")).extracting(node -> node.get("type").asText())
                .contains("REQUIREMENT", "PRD", "UX_TASK", "UX_SPEC");
        assertThat(graph.get("truncated").asBoolean()).isFalse();
    }

    @Test
    void skipUxRequiresEnabledPolicyEligibleLabelAndAuditReason() throws Exception {
        completeMaterials();
        transition(WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "skip-submit", null);
        publishDocument("PRD", "Backend PRD", "Internal API only");

        ResponseEntity<String> disabled = transition(
                WorkflowAction.SKIP_UX, 1, "skip-disabled", "No user interface");
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(disabled.getBody()).at("/details/missing/0").asText())
                .isEqualTo("organizationPolicy.allowSkipUx");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT allow_skip_ux FROM organization_policies WHERE organization_id=?",
                Boolean.class, organizationId)).isFalse();
        jdbcTemplate.update(
                "UPDATE organization_policies SET allow_skip_ux=TRUE,version=version+1 WHERE organization_id=?",
                organizationId);
        ResponseEntity<String> ordinary = transition(
                WorkflowAction.SKIP_UX, 1, "skip-ordinary", "No user interface");
        assertThat(ordinary.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(ordinary.getBody()).at("/details/missing/0").asText())
                .isEqualTo("eligibleSkipUxLabel");

        ResponseEntity<String> labelAdded = csrf().post(
                "/api/v1/work-items/" + requirementId + "/labels?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of("label", "BACKEND_ONLY"),
                ownerCookie,
                String.class);
        assertThat(labelAdded.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> missingReason = transition(
                WorkflowAction.SKIP_UX, 1, "skip-no-reason", "  ");
        assertThat(missingReason.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(missingReason.getBody()).at("/details/missing/0").asText())
                .isEqualTo("reason");

        JsonNode skipped = objectMapper.readTree(transition(
                        WorkflowAction.SKIP_UX, 1, "skip-success", "  API-only change  ")
                .getBody());
        assertThat(skipped.get("status").asText()).isEqualTo("READY_FOR_DEV");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT reason FROM work_item_events WHERE work_item_id = ? AND event_type = 'SKIP_UX'",
                        String.class,
                        requirementId))
                .isEqualTo("API-only change");
    }

    @Test
    void submitForQaExplainsMissingRepositoryAndCiOptionalAllowsCompletedTasks() throws Exception {
        long taskId = prepareDevelopmentRequirement("DONE");

        ResponseEntity<String> required = transition(
                WorkflowAction.SUBMIT_FOR_QA, 0, "qa-required-no-repo", null);

        assertThat(required.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(required.getBody()).at("/details/missing/0").asText())
                .isEqualTo("repository");
        JsonNode summary = objectMapper.readTree(get(
                "/api/v1/development/requirements/" + requirementId + "?organizationId=" + organizationId
                        + "&organizationId=" + organizationId)
                .getBody());
        assertThat(summary.get("ciRequired").asBoolean()).isTrue();
        assertThat(summary.at("/tasks/0/id").asLong()).isEqualTo(taskId);

        jdbcTemplate.update(
                "UPDATE organization_policies SET ci_required=FALSE,version=version+1 WHERE organization_id=?",
                organizationId);

        ResponseEntity<String> submitted = transition(
                WorkflowAction.SUBMIT_FOR_QA, 0, "qa-optional", null);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(submitted.getBody()).get("status").asText())
                .isEqualTo("READY_FOR_QA");
    }

    @Test
    void successfulPipelineForAnOldCommitDoesNotReleaseTheGuard() throws Exception {
        long taskId = prepareDevelopmentRequirement("DONE");
        long repositoryId = insertRepository();
        jdbcTemplate.update(
                "INSERT INTO merge_requests (organization_id,repository_id,work_item_id,remote_mr_iid,title,"
                        + "source_branch,target_branch,state,web_url,head_sha,remote_updated_at,last_synced_at,version) "
                        + "VALUES (?,?,?,7,'MR','feature/task','main','opened','https://gitlab.example/mr/7',"
                        + "'new-head',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                organizationId,
                repositoryId,
                taskId);
        long mergeRequestId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO pipeline_runs (organization_id,repository_id,merge_request_id,remote_pipeline_id,ref,"
                        + "commit_sha,status,web_url,remote_updated_at,last_synced_at,summary_json) "
                        + "VALUES (?,?,?,9,'feature/task','old-head','success','https://gitlab.example/p/9',"
                        + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),JSON_OBJECT())",
                organizationId,
                repositoryId,
                mergeRequestId);

        ResponseEntity<String> response = transition(
                WorkflowAction.SUBMIT_FOR_QA, 0, "qa-stale-pipeline", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(response.getBody()).at("/details/missing/0").asText())
                .isEqualTo("pipelineHeadMismatch");
    }

    @Test
    void completingADevTaskUsesItsVersionAndConcurrentQaSubmissionHasOneWinner() throws Exception {
        long taskId = prepareDevelopmentRequirement("IN_PROGRESS");
        ResponseEntity<String> completed = csrf().post(
                "/api/v1/development/tasks/" + taskId + "/complete",
                Map.of("expectedVersion", 0),
                ownerCookie,
                String.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(completed.getBody()).get("status").asText()).isEqualTo("DONE");
        jdbcTemplate.update(
                "UPDATE organization_policies SET ci_required=FALSE WHERE organization_id=?",
                organizationId);

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> {
                        submitQaAfter(start, "qa-race-a");
                        return null;
                    }),
                    executor.submit(() -> {
                        submitQaAfter(start, "qa-race-b");
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
        }
    }

    @Test
    void relationsRejectDuplicateAndSelfWhileActivityMergesComments() throws Exception {
        long targetId = createWorkItem(organizationId, "Related task", "DEV_TASK");
        ResponseEntity<String> self = csrf().post(
                "/api/v1/work-items/" + requirementId + "/relations?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of("targetId", requirementId, "relationType", "RELATES_TO"),
                ownerCookie,
                String.class);
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> created = csrf().post(
                "/api/v1/work-items/" + requirementId + "/relations?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of("targetId", targetId, "relationType", "DEPENDS_ON"),
                ownerCookie,
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> duplicate = csrf().post(
                "/api/v1/work-items/" + requirementId + "/relations?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of("targetId", targetId, "relationType", "DEPENDS_ON"),
                ownerCookie,
                String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        completeMaterials();
        transition(WorkflowAction.SUBMIT_PRODUCT_REVIEW, 0, "activity-event", null);
        ResponseEntity<String> comment = csrf().post(
                "/api/v1/work-items/" + requirementId + "/comments?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of("body", "  Please verify the API contract.  "),
                ownerCookie,
                String.class);
        assertThat(comment.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode activity = objectMapper.readTree(get("/api/v1/work-items/" + requirementId
                        + "/activity?organizationId=" + organizationId + "&organizationId=" + organizationId)
                .getBody());
        assertThat(activity).hasSize(2);
        assertThat(activity.get(0).get("kind").asText()).isEqualTo("EVENT");
        assertThat(activity.get(1).get("kind").asText()).isEqualTo("COMMENT");
        assertThat(activity.get(1).get("body").asText()).isEqualTo("Please verify the API contract.");
    }

    @Test
    void deliveryGraphUsesScopedBatchSnapshotForWorkItemsDocumentsAndCycles() throws Exception {
        long uxTaskId = createWorkItem(organizationId, "Design login", "UX_TASK");
        JsonNode prd = publishDocument("PRD", "Login PRD", "Goal and acceptance criteria");
        jdbcTemplate.update(
                "INSERT INTO work_item_relations (organization_id,source_id,target_id,relation_type,created_by,created_at) "
                        + "VALUES (?, ?, ?, 'RELATES_TO', ?, UTC_TIMESTAMP(6)), "
                        + "(?, ?, ?, 'DEPENDS_ON', ?, UTC_TIMESTAMP(6))",
                organizationId,
                requirementId,
                uxTaskId,
                ownerId,
                organizationId,
                uxTaskId,
                requirementId,
                ownerId);

        ResponseEntity<String> response = get("/api/v1/work-items/" + requirementId
                + "/delivery-graph?organizationId=" + organizationId + "&organizationId=" + organizationId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode graph = objectMapper.readTree(response.getBody());
        assertThat(graph.get("nodes")).extracting(node -> node.get("id").asText())
                .containsExactly("work-item:" + requirementId, "work-item:" + uxTaskId,
                        "document:" + prd.get("id").asLong());
        assertThat(graph.get("edges")).hasSize(3);
        assertThat(graph.get("truncated").asBoolean()).isFalse();

    }

    private void transitionAfter(CountDownLatch start, String idempotencyKey) throws InterruptedException {
        start.await();
        transitionService.transition(
                ownerId,
                organizationId,
                requirementId,
                WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                0,
                idempotencyKey,
                null);
    }

    private void submitQaAfter(CountDownLatch start, String idempotencyKey) throws InterruptedException {
        start.await();
        transitionService.transition(
                ownerId,
                organizationId,
                requirementId,
                WorkflowAction.SUBMIT_FOR_QA,
                0,
                idempotencyKey,
                null);
    }

    private void completeMaterials() {
        jdbcTemplate.update(
                "INSERT INTO requirement_details (work_item_id, organization_id, goal, in_scope, out_of_scope, "
                        + "acceptance_criteria_json, business_value, updated_at, version) VALUES "
                        + "(?, ?, 'Ship safely', 'Transition API', '', JSON_ARRAY('State changes'), '', UTC_TIMESTAMP(6), 0)",
                requirementId,
                organizationId);
    }

    private long prepareDevelopmentRequirement(String taskStatus) {
        jdbcTemplate.update(
                "UPDATE work_items SET status='IN_DEVELOPMENT' WHERE id=?",
                requirementId);
        jdbcTemplate.update(
                "INSERT INTO work_items (organization_id,item_number,item_key,type,title,description,status,"
                        + "priority,parent_id,reporter_user_id,created_at,updated_at,version) "
                        + "VALUES (?,2,'FORGE-2','DEV_TASK','Implement','',?,'MEDIUM',?,?,"
                        + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                organizationId,
                taskStatus,
                requirementId,
                ownerId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertRepository() {
        jdbcTemplate.update(
                "INSERT INTO secrets (organization_id,type,ciphertext,iv,key_version,fingerprint,created_at) "
                        + "VALUES (?,'GITLAB_TOKEN','cipher','iv',1,'fingerprint',UTC_TIMESTAMP(6))",
                organizationId);
        long secretId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO gitlab_connections (organization_id,name,base_url,credential_secret_id,status,created_by,"
                        + "created_at,updated_at,version) VALUES (?,'GitLab','https://gitlab.example',?,'ACTIVE',?,"
                        + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                organizationId,
                secretId,
                ownerId);
        long connectionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO git_repositories (organization_id,connection_id,remote_project_id,"
                        + "path_with_namespace,http_url,default_branch,status,last_synced_at,created_at,updated_at,version) "
                        + "VALUES (?,?,'100','forge/project','https://gitlab.example/forge/project','main','ACTIVE',"
                        + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                organizationId,
                connectionId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private ResponseEntity<String> transition(
            WorkflowAction action, long expectedVersion, String idempotencyKey, String reason) {
        return transition(action, expectedVersion, idempotencyKey, reason, requirementId, null);
    }

    private ResponseEntity<String> transition(
            WorkflowAction action,
            long expectedVersion,
            String idempotencyKey,
            String reason,
            long workItemId) {
        return transition(action, expectedVersion, idempotencyKey, reason, workItemId, null);
    }

    private ResponseEntity<String> transition(
            WorkflowAction action,
            long expectedVersion,
            String idempotencyKey,
            String reason,
            long workItemId,
            List<String> checklist) {
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("action", action.name());
        body.put("expectedVersion", expectedVersion);
        body.put("idempotencyKey", idempotencyKey);
        if (reason != null) {
            body.put("reason", reason);
        }
        if (checklist != null) {
            body.put("checklist", checklist);
        }
        return csrf().post(
                "/api/v1/work-items/" + workItemId + "/transitions?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                body,
                ownerCookie,
                String.class);
    }

    private JsonNode publishDocument(String type, String title, String text) throws Exception {
        JsonNode document = objectMapper.readTree(csrf().post(
                "/api/v1/documents",
                Map.of("workItemId", requirementId,
                        "type", type, "title", title),
                ownerCookie,
                String.class).getBody());
        long documentId = document.get("id").asLong();
        JsonNode saved = objectMapper.readTree(csrf().post(
                "/api/v1/documents/" + documentId + "/versions?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of("expectedVersion", 0, "content", Map.of("type", "doc", "content", List.of(
                        Map.of("type", "paragraph", "content", List.of(
                                Map.of("type", "text", "text", text)))))),
                ownerCookie,
                String.class).getBody());
        ResponseEntity<String> published = csrf().post(
                "/api/v1/documents/" + documentId + "/publish?organizationId=" + organizationId
                        + "&organizationId=" + organizationId,
                Map.of("versionId", saved.get("currentVersionId").asLong(),
                        "expectedVersion", saved.get("version").asLong()),
                ownerCookie,
                String.class);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(published.getBody());
    }

    private long createWorkItem(long ignoredOrganizationId, String title, String type) throws Exception {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/work-items",
                Map.of(
                        "type", type,
                        "title", title,
                        "description", "relation target",
                        "priority", "MEDIUM"),
                ownerCookie,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asLong();
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
                Map.of("email", ownerEmail, "password", "correct-horse-42"),
                null,
                String.class);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, ownerCookie);
        return csrf().unwrapSuccess(
                restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }
}
