package ai.forge.server.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class MemberRegistrationIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 会话 09 成员账号创建的写请求通过真实 HTTP 与 CSRF 链路执行。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析 CSRF Token 与响应 JSON 的编解码器。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 仅用于搭建初始化事实并断言成员账号与角色的落库结果。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initializeOwner() {
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
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
    }

    @Test
    void ownerCreatesMemberAccountWithRoleAndMemberCanLogin() {
        String ownerCookie = login("owner@example.com", "correct-horse-42");
        long workspaceId = workspaceId();

        ResponseEntity<String> created = register(ownerCookie, workspaceId, "dev@example.com", "Dev Member", "dev-password-42", "DEVELOPER");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"userId\"");
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("workspace_members")).isEqualTo(2);
        assertThat(count("member_roles")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT r.code FROM member_roles mr JOIN roles r ON r.id = mr.role_id "
                        + "JOIN workspace_members wm ON wm.id = mr.workspace_member_id "
                        + "JOIN users u ON u.id = wm.user_id WHERE u.normalized_email = 'dev@example.com'",
                String.class)).isEqualTo("DEVELOPER");

        ResponseEntity<String> login = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", "dev@example.com", "password", "dev-password-42"), null, String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void createMemberRejectsDuplicateEmailWithoutWritingFacts() {
        String ownerCookie = login("owner@example.com", "correct-horse-42");
        long workspaceId = workspaceId();
        assertThat(register(ownerCookie, workspaceId, "dev@example.com", "Dev Member", "dev-password-42", "DEVELOPER")
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> duplicate = register(ownerCookie, workspaceId, "DEV@example.com", "Another Dev", "another-password-84", "PRODUCT");

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("MEMBER_EMAIL_CONFLICT");
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("workspace_members")).isEqualTo(2);
        assertThat(count("member_roles")).isEqualTo(2);
    }

    @Test
    void createMemberRejectsUnknownRole() {
        String ownerCookie = login("owner@example.com", "correct-horse-42");
        long workspaceId = workspaceId();

        ResponseEntity<String> response = register(ownerCookie, workspaceId, "dev@example.com", "Dev Member", "dev-password-42", "NOT_A_ROLE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_FAILED");
        assertThat(count("users")).isOne();
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }

    private ResponseEntity<String> register(String cookie, long workspaceId, String email, String displayName, String password, String role) {
        return csrf().post(
                "/api/v1/workspaces/" + workspaceId + "/members/register",
                Map.of("email", email, "displayName", displayName, "password", password, "role", role),
                cookie,
                String.class);
    }

    private String login(String email, String password) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login", Map.of("email", email, "password", password), null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private long workspaceId() {
        return jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'engineering'", Long.class);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
