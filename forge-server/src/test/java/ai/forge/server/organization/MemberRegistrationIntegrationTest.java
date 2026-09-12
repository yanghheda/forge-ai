package ai.forge.server.organization;

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
                "organization_item_sequences", "organization_policies", "audit_logs", "member_roles",
                "organization_members", "organizations", "users")) {
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
    }

    @Test
    void applicantWaitsForOwnerApprovalBeforeLogin() throws Exception {
        String ownerCookie = login("owner@example.com", "correct-horse-42");

        ResponseEntity<String> created = register("dev@example.com", "Dev Member", "dev-password-42", "DEVELOPER");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"userId\"");
        assertThat(created.getBody()).contains("\"status\":\"PENDING\"");
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("organization_members")).isEqualTo(2);
        assertThat(count("member_roles")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT r.code FROM member_roles mr JOIN roles r ON r.id = mr.role_id "
                        + "JOIN organization_members wm ON wm.id = mr.organization_member_id "
                        + "JOIN users u ON u.id = wm.user_id WHERE u.normalized_email = 'dev@example.com'",
                String.class)).isEqualTo("DEVELOPER");

        ResponseEntity<String> pendingLogin = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", "dev@example.com", "password", "dev-password-42"), null, String.class);
        assertThat(pendingLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        long userId = objectMapper.readTree(created.getBody()).get("userId").asLong();
        ResponseEntity<String> approved = csrf().patch(
                "/api/v1/members/" + userId,
                Map.of(
                        "displayName", "Dev Member",
                        "status", "ACTIVE",
                        "roles", List.of("DEVELOPER", "ADMIN", "RELEASE_APPROVER"),
                        "expectedVersion", 0),
                ownerCookie,
                String.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody()).contains("\"roles\":[\"ADMIN\",\"DEVELOPER\",\"RELEASE_APPROVER\"]");
        ResponseEntity<String> login = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", "dev@example.com", "password", "dev-password-42"), null, String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void createMemberRejectsDuplicateEmailWithoutWritingFacts() {
        assertThat(register("dev@example.com", "Dev Member", "dev-password-42", "DEVELOPER")
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> duplicate = register("DEV@example.com", "Another Dev", "another-password-84", "PRODUCT");

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("MEMBER_EMAIL_CONFLICT");
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("organization_members")).isEqualTo(2);
        assertThat(count("member_roles")).isEqualTo(2);
    }

    @Test
    void createMemberRejectsUnknownRole() {
        ResponseEntity<String> response = register("dev@example.com", "Dev Member", "dev-password-42", "NOT_A_ROLE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_FAILED");
        assertThat(count("users")).isOne();
    }

    @Test
    void ownerCannotEditOwnAccount() {
        String ownerCookie = login("owner@example.com", "correct-horse-42");
        long ownerUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'owner@example.com'", Long.class);

        ResponseEntity<String> response = csrf().patch(
                "/api/v1/members/" + ownerUserId,
                Map.of(
                        "displayName", "Changed Owner",
                        "status", "ACTIVE",
                        "roles", List.of("ADMIN"),
                        "expectedVersion", 0),
                ownerCookie,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_name FROM users WHERE id = ?", String.class, ownerUserId)).isEqualTo("Forge Owner");
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }

    private ResponseEntity<String> register(String email, String displayName, String password, String role) {
        return csrf().post(
                "/api/v1/auth/register",
                Map.of("email", email, "displayName", displayName, "password", password, "role", role),
                null,
                String.class);
    }

    private String login(String email, String password) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login", Map.of("email", email, "password", password), null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
