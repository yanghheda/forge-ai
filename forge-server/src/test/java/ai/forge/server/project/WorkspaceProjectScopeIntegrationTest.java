package ai.forge.server.project;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkspaceProjectScopeIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 会话 09 所有写请求均通过真实 HTTP 与 CSRF 链路执行。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 用于获取真实 CSRF Token 的 JSON 编解码器。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 仅用于搭建会话 09 所需的初始化事实与断言落库结果。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initializeOwner() {
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "comments", "work_item_relations", "work_item_labels", "project_policies", "work_item_events",
                "review_records", "requirement_details", "work_items", "project_item_sequences",
                "project_members", "projects", "audit_logs", "member_roles",
                "workspace_members", "workspaces", "organizations", "users")) {
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
    void ownerCanCreateProjectWithinAnAccessibleWorkspace() throws Exception {
        String cookie = loginOwner();

        ResponseEntity<String> created = createProject(cookie, workspaceId(), "FORGE");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"key\":\"FORGE\"").contains("\"status\":\"ACTIVE\"");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM projects", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM project_members", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM project_item_sequences WHERE project_id = (SELECT id FROM projects WHERE `key` = 'FORGE')",
                        Integer.class))
                .isOne();
    }

    @Test
    void duplicateProjectKeyIsRejectedAndArchivePreservesProjectWithOptimisticLock() throws Exception {
        String cookie = loginOwner();
        long projectId = projectId(createProject(cookie, workspaceId(), "FORGE"));

        ResponseEntity<String> duplicate = createProject(cookie, workspaceId(), "forge");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("PROJECT_KEY_CONFLICT");

        ResponseEntity<String> archived = csrf().patch(
                "/api/v1/projects/" + projectId + "?workspaceId=" + workspaceId(),
                Map.of("expectedVersion", 0),
                cookie,
                String.class);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM projects WHERE id = ?", Integer.class, projectId))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status = 'ARCHIVED' AND archived_at IS NOT NULL AND version = 1 FROM projects WHERE id = ?",
                Boolean.class,
                projectId)).isTrue();

        ResponseEntity<String> staleArchive = csrf().patch(
                "/api/v1/projects/" + projectId + "?workspaceId=" + workspaceId(),
                Map.of("expectedVersion", 0),
                cookie,
                String.class);
        assertThat(staleArchive.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleArchive.getBody()).contains("VERSION_CONFLICT");
    }

    @Test
    void projectMemberRemovalAndWorkspaceMemberRemovalBothInvalidateProjectAccess() throws Exception {
        String ownerCookie = loginOwner();
        long workspaceId = workspaceId();
        long projectId = projectId(createProject(ownerCookie, workspaceId, "FORGE"));
        createUser("member@example.com");

        assertThat(csrf().post(
                "/api/v1/workspaces/" + workspaceId + "/members",
                Map.of("email", "member@example.com"), ownerCookie, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(csrf().post(
                "/api/v1/projects/" + projectId + "/members?workspaceId=" + workspaceId,
                Map.of("email", "member@example.com"), ownerCookie, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        grantWorkspaceRole(workspaceId, "member@example.com", "PRODUCT");

        String memberCookie = login("member@example.com");
        assertThat(get("/api/v1/projects/" + projectId + "?workspaceId=" + workspaceId, memberCookie).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        long memberId = userId("member@example.com");
        assertThat(csrf().delete(
                "/api/v1/projects/" + projectId + "/members/" + memberId + "?workspaceId=" + workspaceId,
                ownerCookie,
                String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/projects/" + projectId + "?workspaceId=" + workspaceId, memberCookie).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(csrf().post(
                "/api/v1/projects/" + projectId + "/members?workspaceId=" + workspaceId,
                Map.of("email", "member@example.com"), ownerCookie, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(csrf().delete(
                "/api/v1/workspaces/" + workspaceId + "/members/" + memberId,
                ownerCookie,
                String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/projects/" + projectId + "?workspaceId=" + workspaceId, memberCookie).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void userCannotReadOrArchiveProjectInAnotherWorkspaceEvenWithKnownId() throws Exception {
        String ownerCookie = loginOwner();
        long firstWorkspaceId = workspaceId();
        createUser("member@example.com");
        csrf().post(
                "/api/v1/workspaces/" + firstWorkspaceId + "/members",
                Map.of("email", "member@example.com"), ownerCookie, String.class);
        long otherProjectId = projectId(createProject(ownerCookie, createWorkspace(ownerCookie, "quality"), "QA"));
        String memberCookie = login("member@example.com");

        ResponseEntity<String> read = get("/api/v1/projects/" + otherProjectId + "?workspaceId=" + createWorkspaceId("quality"), memberCookie);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(read.getBody()).contains("RESOURCE_NOT_FOUND");
        ResponseEntity<String> archive = csrf().patch(
                "/api/v1/projects/" + otherProjectId + "?workspaceId=" + createWorkspaceId("quality"),
                Map.of("expectedVersion", 0), memberCookie, String.class);
        assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(archive.getBody()).contains("RESOURCE_NOT_FOUND");
    }

    @Test
    void ownerCannotRemoveTheirOwnWorkspaceOrProjectMembership() throws Exception {
        String cookie = loginOwner();
        long workspaceId = workspaceId();
        long projectId = projectId(createProject(cookie, workspaceId, "FORGE"));
        long ownerId = userId("owner@example.com");

        ResponseEntity<String> workspaceRemoval = csrf().delete(
                "/api/v1/workspaces/" + workspaceId + "/members/" + ownerId, cookie, String.class);
        assertThat(workspaceRemoval.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(workspaceRemoval.getBody()).contains("VALIDATION_FAILED");

        ResponseEntity<String> projectRemoval = csrf().delete(
                "/api/v1/projects/" + projectId + "/members/" + ownerId + "?workspaceId=" + workspaceId,
                cookie,
                String.class);
        assertThat(projectRemoval.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(projectRemoval.getBody()).contains("VALIDATION_FAILED");
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }

    private String loginOwner() {
        return login("owner@example.com");
    }

    private String login(String email) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login", Map.of("email", email, "password", "correct-horse-42"), null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private long workspaceId() {
        return jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'engineering'", Long.class);
    }

    private long createWorkspace(String cookie, String slug) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/workspaces", Map.of("name", "Quality", "slug", slug), cookie, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return createWorkspaceId(slug);
    }

    private long createWorkspaceId(String slug) {
        return jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, slug);
    }

    private ResponseEntity<String> createProject(String cookie, long workspaceId, String key) {
        return csrf().post(
                "/api/v1/projects",
                Map.of("workspaceId", workspaceId, "key", key, "name", "ForgeAI", "description", "会话 09 项目"),
                cookie,
                String.class);
    }

    private long projectId(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        return body.get("id").asLong();
    }

    private void createUser(String email) {
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE normalized_email = 'owner@example.com'", String.class);
        jdbcTemplate.update(
                "INSERT INTO users (email, normalized_email, display_name, password_hash, status, failed_login_count, "
                        + "locked_until, last_login_at, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'Project Member', ?, 'ACTIVE', 0, NULL, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                email,
                email,
                passwordHash);
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE normalized_email = ?", Long.class, email);
    }

    private void grantWorkspaceRole(long workspaceId, String email, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO member_roles (workspace_member_id, role_id, project_id, created_at) "
                        + "SELECT wm.id, r.id, NULL, UTC_TIMESTAMP(6) FROM workspace_members wm CROSS JOIN roles r "
                        + "JOIN users u ON u.id = wm.user_id WHERE wm.workspace_id = ? AND u.normalized_email = ? AND r.code = ?",
                workspaceId, email, roleCode);
    }

    private ResponseEntity<String> get(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        return new CsrfTestClient(restTemplate, objectMapper).unwrapSuccess(
                restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }
}
