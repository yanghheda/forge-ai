package ai.forge.server.workitem;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class OrganizationRequirementExperienceIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 HTTP、Session 与 CSRF 链路验证合并后的产品入口。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析统一响应信封中的组织、需求与统计结果。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 仅用于清理测试事实和验证内部兼容 scope 未暴露给调用方。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CsrfTestClient csrf;

    @BeforeEach
    void resetFacts() {
        csrf = new CsrfTestClient(restTemplate, objectMapper);
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "requirement_participants", "work_item_events", "review_records", "requirement_details",
                "work_items", "organization_item_sequences", "organization_policies",                 "organization_policies", "audit_logs", "member_roles", "organization_members", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void initializesOneVisibleOrganizationAndCreatesDefaultRequirementScope() throws Exception {
        ResponseEntity<String> response = csrf.post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "owner@example.com",
                        "adminDisplayName", "Owner",
                        "password", "correct-horse-42",
                        "organizationName", "独立开发者",
                        "organizationSlug", "solo",
                        "logoFileName", "logo.webp",
                        "logoMediaType", "image/webp",
                        "logoBase64", "UklGRgAAAABXRUJQVlA4WAAAAAAAAAAAGwAAGwAA"),
                null,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.has("organizationId")).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM organizations", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT default_organization_id IS NOT NULL FROM instance_settings WHERE id = 1",
                Boolean.class)).isTrue();
    }

    @Test
    void membersSelfRegisterAndRequirementSupportsRoleParticipantsAndMyList() throws Exception {
        initializeCompany();
        String ownerCookie = login("owner@example.com", "correct-horse-42");

        JsonNode product = register("product@example.com", "产品同学", "member-password-42", "PRODUCT");
        JsonNode developer = register("dev@example.com", "开发同学", "member-password-84", "DEVELOPER");
        approveMember(ownerCookie, product.get("userId").asLong(), "PRODUCT");
        approveMember(ownerCookie, developer.get("userId").asLong(), "DEVELOPER");
        ResponseEntity<String> forbiddenOwner = csrf.post(
                "/api/v1/auth/register",
                Map.of("email", "other-owner@example.com", "displayName", "Other", "password", "member-password-21", "role", "OWNER"),
                null,
                String.class);
        assertThat(forbiddenOwner.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> created = csrf.post(
                "/api/v1/requirements",
                Map.of("title", "Team registration", "description", "不再选择工作区和项目", "priority", "HIGH"),
                ownerCookie,
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode requirement = objectMapper.readTree(created.getBody());
        assertThat(requirement.has("organizationId")).isFalse();
        assertThat(requirement.has("organizationId")).isFalse();

        ResponseEntity<String> assigned = csrf.put(
                "/api/v1/requirements/" + requirement.get("id").asLong() + "/participants",
                Map.of("participants", List.of(
                        Map.of("role", "PRODUCT", "userId", product.get("userId").asLong()),
                        Map.of("role", "DEVELOPER", "userId", developer.get("userId").asLong()))),
                ownerCookie,
                String.class);
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(assigned.getBody()).size()).isEqualTo(2);

        String developerCookie = login("dev@example.com", "member-password-84");
        JsonNode mine = get("/api/v1/requirements?mine=true&q=Team", developerCookie);
        assertThat(mine.get("total").asLong()).isOne();
        assertThat(mine.get("items").get(0).get("title").asText()).isEqualTo("Team registration");

        JsonNode overview = get("/api/v1/requirements/overview", ownerCookie);
        assertThat(overview.get("total").asLong()).isOne();
        assertThat(overview.get("inProgress").asLong()).isOne();
        assertThat(overview.get("completed").asLong()).isZero();
    }

    private void initializeCompany() {
        ResponseEntity<String> response = csrf.post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "owner@example.com",
                        "adminDisplayName", "Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge",
                        "logoFileName", "logo.webp",
                        "logoMediaType", "image/webp",
                        "logoBase64", "UklGRgAAAABXRUJQVlA4WAAAAAAAAAAAGwAAGwAA"),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode register(String email, String displayName, String password, String role) throws Exception {
        ResponseEntity<String> response = csrf.post(
                "/api/v1/auth/register",
                Map.of("email", email, "displayName", displayName, "password", password, "role", role),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody());
    }

    private void approveMember(String ownerCookie, long userId, String role) {
        ResponseEntity<String> response = csrf.patch(
                "/api/v1/members/" + userId,
                Map.of("status", "ACTIVE", "role", role, "expectedVersion", 0),
                ownerCookie,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String login(String email, String password) {
        ResponseEntity<String> response = csrf.post(
                "/api/v1/auth/login", Map.of("email", email, "password", password), null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private JsonNode get(String path, String cookie) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        ResponseEntity<String> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("data");
    }
}
