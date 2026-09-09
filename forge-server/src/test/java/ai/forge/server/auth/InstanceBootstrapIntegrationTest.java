package ai.forge.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.auth.application.InstanceBootstrapService;
import ai.forge.server.auth.domain.BootstrapCommand;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(OutputCaptureExtension.class)
class InstanceBootstrapIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InstanceBootstrapService bootstrapService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetBootstrapFacts() {
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "work_item_events", "review_records", "requirement_details", "work_items", "project_item_sequences", "project_members", "projects", "audit_logs", "member_roles",
                "workspace_members", "workspaces", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void initializesCompleteIdentityGraphWithoutExposingPassword(CapturedOutput output) {
        String password = "correct-horse-42";
        ResponseEntity<String> response = initialize(password);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/api/v1/organization");
        assertThat(response.getBody()).contains("\"organizationSlug\":\"forge\"")
                .doesNotContain("workspaceId")
                .doesNotContain("projectId")
                .doesNotContain(password)
                .doesNotContain("passwordHash");
        assertThat(count("users")).isOne();
        assertThat(count("organizations")).isOne();
        assertThat(count("workspaces")).isOne();
        assertThat(count("projects")).isOne();
        assertThat(count("project_item_sequences")).isOne();
        assertThat(count("workspace_members")).isOne();
        assertThat(count("member_roles")).isOne();
        assertThat(count("audit_logs")).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT normalized_email FROM users", String.class))
                .isEqualTo("owner@example.com");
        assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM users", String.class))
                .startsWith("$2b$12$")
                .doesNotContain(password);
        assertThat(jdbcTemplate.queryForObject("SELECT initialized_at IS NOT NULL FROM instance_settings", Boolean.class))
                .isTrue();
        assertThat(output.getAll()).doesNotContain(password).doesNotContain("$2b$12$");
    }

    @Test
    void rejectsRepeatedInitializationWithoutAdditionalRows() {
        assertThat(initialize("correct-horse-42").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> repeated = initialize("another-secure-84");

        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repeated.getBody()).contains("INSTANCE_ALREADY_INITIALIZED");
        assertThat(count("users")).isOne();
        assertThat(count("audit_logs")).isOne();
    }

    @Test
    void serializesConcurrentInitializationSoExactlyOneSucceeds() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<ResponseEntity<String>>> requests = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            requests.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                return initialize("correct-horse-42");
            }));
        }
        start.countDown();

        List<HttpStatus> statuses = requests.stream().map(CompletableFuture::join).map(response ->
                HttpStatus.valueOf(response.getStatusCode().value())).toList();
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(count("users")).isOne();
        assertThat(count("workspaces")).isOne();
        assertThat(count("projects")).isOne();
        assertThat(count("audit_logs")).isOne();
    }

    @Test
    void rejectsWeakPasswordWithoutWritingFacts() {
        ResponseEntity<String> response = initialize("weak1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_FAILED").doesNotContain("weak1");
        assertThat(count("users")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT initialized_at IS NULL FROM instance_settings", Boolean.class))
                .isTrue();
    }

    @Test
    void rollsBackEveryFactWhenMidTransactionWriteFails() {
        String oversizedWorkspaceSlug = "w".repeat(81);
        BootstrapCommand command = new BootstrapCommand(
                "owner@example.com",
                "Forge Owner",
                "correct-horse-42",
                "Forge",
                "forge",
                "Engineering",
                oversizedWorkspaceSlug,
                "req_transaction_rollback");

        assertThatThrownBy(() -> bootstrapService.initialize(command)).isInstanceOf(RuntimeException.class);
        for (String table : List.of(
                "users", "organizations", "workspaces", "workspace_members", "member_roles", "audit_logs")) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT initialized_at IS NULL AND default_organization_id IS NULL FROM instance_settings",
                        Boolean.class))
                .isTrue();
    }

    @Test
    void publishesInitializationEndpointInOpenApiWithoutPasswordResponseField() {
        String openApi = restTemplate.getForObject("/v3/api-docs", String.class);

        assertThat(openApi).contains("/api/v1/setup/initialize").contains("adminEmail");
        assertThat(openApi).doesNotContain("passwordHash");
    }

    private ResponseEntity<String> initialize(String password) {
        return new CsrfTestClient(restTemplate, objectMapper).post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "Owner@Example.COM",
                        "adminDisplayName", "Forge Owner",
                        "password", password,
                        "organizationName", "Forge",
                        "organizationSlug", "forge",
                        "workspaceName", "Engineering",
                        "workspaceSlug", "engineering"), null,
                String.class);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent initialization test interrupted", exception);
        }
    }
}
