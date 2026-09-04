package ai.forge.server.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

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

    private String ownerCookie;
    private long workspaceId;
    private long projectId;

    @BeforeEach
    void initializeProject() throws Exception {
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "outbox_events", "agent_tool_calls", "approvals", "agent_events", "agent_steps", "agent_runs", "comments", "work_item_relations",
                "work_item_labels", "project_policies", "work_item_events", "review_records",
                "requirement_details", "work_items", "project_item_sequences", "project_members", "projects",
                "audit_logs", "member_roles", "workspace_members", "workspaces", "organizations", "users")) {
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
        ownerCookie = loginOwner();
        workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'engineering'", Long.class);
        ResponseEntity<String> project = csrf().post(
                "/api/v1/projects",
                Map.of("workspaceId", workspaceId, "key", "FORGE", "name", "ForgeAI", "description", "会话 18"),
                ownerCookie,
                String.class);
        assertThat(project.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        projectId = objectMapper.readTree(project.getBody()).get("id").asLong();
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
        assertThat(terminal.get("lastSequence").asLong()).isEqualTo(5);
        assertThat(terminal.get("steps")).hasSize(1);
        assertThat(terminal.get("steps").get(0).get("status").asText()).isEqualTo("SUCCEEDED");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT message_redacted FROM agent_runs WHERE id = ?", String.class, runId))
                .doesNotContain("should-not-be-persisted")
                .contains("chars");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT sequence FROM agent_events WHERE run_id = ? ORDER BY sequence", Long.class, runId))
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM agent_events WHERE run_id = ? ORDER BY sequence", String.class, runId))
                .containsExactly("agent.queued", "agent.started", "step.started", "step.completed", "agent.completed");
    }

    @Test
    void clientRequestIdIsIdempotentForTheSameUserAndProject() throws Exception {
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
    void mediumToolConfirmationDefaultsToAskAndExplicitPolicyIsPersisted() throws Exception {
        String defaultRunId = data(createRun("default policy", "policy-default")).get("id").asText();
        String allowedRunId = data(createRun("explicit allow", "policy-allow", "ALLOW")).get("id").asText();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT medium_tool_confirmation FROM agent_runs WHERE id = ?", String.class, defaultRunId))
                .isEqualTo("ASK");
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
    void knownRunIdCannotCrossWorkspaceOrProjectScope() throws Exception {
        String runId = data(createRun("scope", "scope-request")).get("id").asText();

        assertThat(getRun(runId, workspaceId + 999, projectId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getRun(runId, workspaceId, projectId + 999).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void terminalStreamReplaysAfterLastEventIdAndThenCloses() throws Exception {
        String runId = data(createRun("stream", "stream-request")).get("id").asText();
        JsonNode terminal = awaitTerminal(runId);
        assertThat(terminal.get("lastSequence").asLong()).isEqualTo(5);

        org.springframework.http.HttpHeaders headers = headers(ownerCookie);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        headers.set("Last-Event-ID", "2");
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/agent-runs/" + runId + "/events?workspaceId=" + workspaceId + "&projectId=" + projectId,
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getBody())
                .contains("id:3", "event:step.started", "id:4", "event:step.completed", "id:5", "event:agent.completed")
                .doesNotContain("id:1", "id:2")
                .doesNotContain("should-not-be-persisted");

        ResponseEntity<String> caughtUp = restTemplate.exchange(
                "/api/v1/agent-runs/" + runId + "/events?workspaceId=" + workspaceId + "&projectId=" + projectId
                        + "&afterSequence=" + terminal.get("lastSequence").asLong(),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);
        assertThat(caughtUp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(caughtUp.getBody()).isNullOrEmpty();
    }

    @Test
    void streamScopeViolationIsHiddenAsNotFound() throws Exception {
        String runId = data(createRun("stream scope", "stream-scope")).get("id").asText();
        org.springframework.http.HttpHeaders headers = headers(ownerCookie);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/agent-runs/" + runId + "/events?workspaceId=" + workspaceId
                        + "&projectId=" + (projectId + 999),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("RESOURCE_NOT_FOUND");
    }

    private ResponseEntity<String> createRun(String message, String clientRequestId) {
        return createRun(message, clientRequestId, null);
    }

    private ResponseEntity<String> createRun(String message, String clientRequestId, String mediumToolConfirmation) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("workspaceId", workspaceId);
        body.put("projectId", projectId);
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
            ResponseEntity<String> response = getRun(runId, workspaceId, projectId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            snapshot = data(response);
            if (snapshot.get("terminal").asBoolean()) {
                return snapshot;
            }
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Fake dispatcher did not finish run before deadline");
    }

    private ResponseEntity<String> getRun(String runId, long requestedWorkspaceId, long requestedProjectId) {
        return restTemplate.exchange(
                "/api/v1/agent-runs/" + runId + "?workspaceId=" + requestedWorkspaceId
                        + "&projectId=" + requestedProjectId,
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
