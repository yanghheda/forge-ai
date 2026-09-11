package ai.forge.server.workitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.workitem.application.WorkItemCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class CreateUxTaskIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过应用服务入口验证 createUxTask 规则；Agent Tool 内部执行将复用同一路径。 */
    @Autowired
    private WorkItemCommandService commandService;

    /* 通过真实 HTTP 链路搭建身份与项目事实。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析初始化与工作项响应。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 仅用于搭建身份事实并断言持久化结果。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String ownerCookie;
    private long ownerId;
    private long organizationId;

    private long requirementId;

    @BeforeEach
    void initializeRequirement() throws Exception {
        String ownerEmail = "owner-" + java.util.UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 "
                        + "WHERE id = 1");
        jdbcTemplate.update("UPDATE work_items SET parent_id = NULL");
        for (String table : List.of(
                "outbox_events", "work_item_relations", "work_item_labels", "organization_policies", "work_item_events",
                "review_records", "requirement_details", "work_items", "organization_item_sequences",                 "organization_policies", "audit_logs", "member_roles", "organization_members", "organizations",
                "users")) {
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
        ownerCookie = login(ownerEmail);
        ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = ?", Long.class, ownerEmail);
        organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1", Long.class);
        ResponseEntity<String> requirement = csrf().post(
                "/api/v1/work-items",
                Map.of(
                        "type", "REQUIREMENT",
                        "title", "UX 跟随的需求",
                        "description", "parent fact",
                        "priority", "HIGH"),
                ownerCookie,
                String.class);
        requirementId = objectMapper.readTree(requirement.getBody()).get("id").asLong();
    }

    @Test
    void createUxTaskPersistsParentLinkAndServerControlledDefaults() {
        var uxTask = commandService.createUxTask(
                ownerId, organizationId, requirementId, "注册流程重设计", "降低放弃率", null, null);

        assertThat(uxTask.type().name()).isEqualTo("UX_TASK");
        assertThat(uxTask.status().name()).isEqualTo("TODO");
        assertThat(uxTask.title()).isEqualTo("注册流程重设计");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT parent_id FROM work_items WHERE id = ?", Long.class, uxTask.id()))
                .isEqualTo(requirementId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT item_number FROM work_items WHERE id = ?", Long.class, requirementId) + 1)
                .isEqualTo(uxTask.itemNumber());
    }

    @Test
    void createUxTaskRejectsMissingOrNonRequirementParent() {
        long nonRequirementParent = createNonRequirementParent();

        assertThatThrownBy(() -> commandService.createUxTask(
                ownerId, organizationId, 999_999L, "孤儿", null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> commandService.createUxTask(
                ownerId, organizationId, nonRequirementParent, "错误父项", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_items WHERE type = 'UX_TASK'", Long.class))
                .isZero();
    }

    @Test
    void createUxTaskRejectsNonRequirementParent() {
        var firstUxTask = commandService.createUxTask(
                ownerId, organizationId, requirementId, "第一个 UX Task", null, null, null);

        assertThatThrownBy(() -> commandService.createUxTask(
                ownerId, organizationId, firstUxTask.id(), "嵌套 UX Task", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createUxTaskFailsWhenUxCreatePermissionIsRevoked() {
        jdbcTemplate.update(
                "DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE code = 'OWNER') "
                        + "AND permission_id = (SELECT id FROM permissions WHERE code = 'ux.create')");

        assertThatThrownBy(() -> commandService.createUxTask(
                ownerId, organizationId, requirementId, "权限撤回后的创建", null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_items WHERE type = 'UX_TASK'", Long.class))
                .isZero();
    }

    @org.junit.jupiter.api.AfterEach
    void restoreOwnerUxCreatePermissionForTheNextTestMethod() {
        jdbcTemplate.update(
                "INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES "
                        + "((SELECT id FROM roles WHERE code = 'OWNER'), "
                        + "(SELECT id FROM permissions WHERE code = 'ux.create'))");
    }

    private long createNonRequirementParent() {
        try {
            ResponseEntity<String> task = csrf().post(
                    "/api/v1/work-items",
                    Map.of(
                            "type", "DEV_TASK",
                            "title", "不能作为 UX 父项的开发任务",
                            "description", "invalid parent fact",
                            "priority", "MEDIUM"),
                    ownerCookie,
                    String.class);
            return objectMapper.readTree(task.getBody()).get("id").asLong();
        } catch (Exception exception) {
            throw new IllegalStateException("Non-requirement parent was not created", exception);
        }
    }

    private String login(String email) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "correct-horse-42"),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return response.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE);
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }
}
