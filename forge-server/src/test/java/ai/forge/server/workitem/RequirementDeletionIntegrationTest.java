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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class RequirementDeletionIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 HTTP、Session 与 CSRF 链路验证需求软删除入口。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析统一响应信封中的需求与统计结果。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 仅用于清理测试事实并直接改写需求状态以驱动非草稿场景。 */
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
                "work_items", "organization_item_sequences", "organization_policies", "audit_logs",
                "member_roles", "organization_members", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void softDeletesDraftRequirementAndRemovesItFromListing() throws Exception {
        initializeCompany();
        String ownerCookie = login("owner@example.com", "correct-horse-42");
        JsonNode created = createRequirement(ownerCookie, "待删除草稿需求");
        long requirementId = created.get("id").asLong();
        long version = created.get("version").asLong();
        long totalBefore = listTotal(ownerCookie);

        ResponseEntity<String> deleted = csrf.delete(
                "/api/v1/requirements/" + requirementId + "?expectedVersion=" + version,
                ownerCookie,
                String.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getStatus("/api/v1/requirements/" + requirementId, ownerCookie))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(listTotal(ownerCookie)).isEqualTo(totalBefore - 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM work_items WHERE id = ?",
                Boolean.class,
                requirementId)).isTrue();
    }

    @Test
    void rejectsDeletionOfNonDraftRequirement() throws Exception {
        initializeCompany();
        String ownerCookie = login("owner@example.com", "correct-horse-42");
        JsonNode created = createRequirement(ownerCookie, "非草稿需求");
        long requirementId = created.get("id").asLong();
        jdbcTemplate.update(
                "UPDATE work_items SET status = 'REQUIREMENT_APPROVAL' WHERE id = ?", requirementId);

        ResponseEntity<String> deleted = csrf.delete(
                "/api/v1/requirements/" + requirementId + "?expectedVersion=" + created.get("version").asLong(),
                ownerCookie,
                String.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM work_items WHERE id = ?",
                Boolean.class,
                requirementId)).isTrue();
    }

    @Test
    void rejectsDeletionWithStaleVersion() throws Exception {
        initializeCompany();
        String ownerCookie = login("owner@example.com", "correct-horse-42");
        JsonNode created = createRequirement(ownerCookie, "版本过期需求");
        long requirementId = created.get("id").asLong();
        long staleVersion = created.get("version").asLong() + 99;

        ResponseEntity<String> deleted = csrf.delete(
                "/api/v1/requirements/" + requirementId + "?expectedVersion=" + staleVersion,
                ownerCookie,
                String.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM work_items WHERE id = ?",
                Boolean.class,
                requirementId)).isTrue();
    }

    @Test
    void returnsNotFoundForRepeatedOrMissingDeletion() throws Exception {
        initializeCompany();
        String ownerCookie = login("owner@example.com", "correct-horse-42");
        JsonNode created = createRequirement(ownerCookie, "重复删除需求");
        long requirementId = created.get("id").asLong();
        long version = created.get("version").asLong();

        ResponseEntity<String> first = csrf.delete(
                "/api/v1/requirements/" + requirementId + "?expectedVersion=" + version,
                ownerCookie,
                String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> repeated = csrf.delete(
                "/api/v1/requirements/" + requirementId + "?expectedVersion=" + (version + 1),
                ownerCookie,
                String.class);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> missing = csrf.delete(
                "/api/v1/requirements/999999?expectedVersion=0", ownerCookie, String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void initializeCompany() {
        ResponseEntity<String> response = csrf.post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "owner@example.com",
                        "adminDisplayName", "Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge"),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode createRequirement(String cookie, String title) throws Exception {
        ResponseEntity<String> response = csrf.post(
                "/api/v1/requirements",
                Map.of("title", title, "priority", "MEDIUM"),
                cookie,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody());
    }

    private long listTotal(String cookie) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/requirements", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("data").get("total").asLong();
    }

    private HttpStatusCode getStatus(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        ResponseEntity<String> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getStatusCode();
    }

    private String login(String email, String password) {
        ResponseEntity<String> response = csrf.post(
                "/api/v1/auth/login", Map.of("email", email, "password", password), null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }
}
