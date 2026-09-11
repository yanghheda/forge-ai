package ai.forge.server.workitem;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

class WorkItemIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 HTTP、Session 与 CSRF 链路调用本轮公开 API。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 读取分页和资源响应，避免用字符串顺序形成脆弱断言。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 仅搭建测试事实、模拟逻辑删除并检查真实 MySQL 查询计划。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /* 并发性质测试通过真实应用服务代理进入事务和 MyBatis 持久化。 */
    @Autowired
    private WorkItemCommandService commandService;

    private String ownerCookie;
    private long organizationId;


    @BeforeEach
    void initializeOrganization() throws Exception {
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "comments", "work_item_relations", "work_item_labels", "organization_policies", "work_item_events",
                "review_records", "requirement_details", "work_items", "organization_item_sequences",
                "organization_policies", "audit_logs", "member_roles",
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
        ownerCookie = loginOwner();
        organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1", Long.class);
    }

    @Test
    void createsRequirementAndTasksWithServerGeneratedKeysAndFixedInitialStatuses() throws Exception {
        JsonNode requirement = create("REQUIREMENT", "Atomic work items", "HIGH");
        JsonNode uxTask = create("UX_TASK", "Design list", "MEDIUM");
        JsonNode devTask = create("DEV_TASK", "Build API", "URGENT");
        JsonNode qaTask = create("QA_TASK", "Verify concurrency", "LOW");

        assertThat(requirement.get("itemKey").asText()).isEqualTo("REQ-1");
        assertThat(requirement.get("itemNumber").asLong()).isEqualTo(1);
        assertThat(requirement.get("status").asText()).isEqualTo("DRAFT");
        assertThat(uxTask.get("status").asText()).isEqualTo("TODO");
        assertThat(devTask.get("status").asText()).isEqualTo("TODO");
        assertThat(qaTask.get("status").asText()).isEqualTo("TODO");
        assertThat(qaTask.get("itemKey").asText()).isEqualTo("REQ-4");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT next_value FROM organization_item_sequences WHERE organization_id = ?", Long.class, organizationId))
                .isEqualTo(5);
    }

    @Test
    void readsSummaryPagesThroughScopeAndUsesThePaginationIndex() throws Exception {
        create("REQUIREMENT", "First", "MEDIUM");
        create("DEV_TASK", "Second", "MEDIUM");
        create("DEV_TASK", "Third", "HIGH");

        ResponseEntity<String> response = get("/api/v1/work-items?type=DEV_TASK&status=TODO&page=1&pageSize=1", ownerCookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = objectMapper.readTree(response.getBody());
        assertThat(page.get("page").asInt()).isEqualTo(1);
        assertThat(page.get("pageSize").asInt()).isEqualTo(1);
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(page.get("items").size()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("itemKey").asText()).isEqualTo("REQ-3");
        assertThat(page.get("items").get(0).has("description")).isFalse();

        List<String> usedKeys = jdbcTemplate.query(
                "EXPLAIN SELECT id FROM work_items FORCE INDEX (idx_work_items_scope_type_status_page) "
                        + "WHERE organization_id = ? AND type = 'DEV_TASK' "
                        + "AND status = 'TODO' AND deleted_at IS NULL "
                        + "ORDER BY item_number DESC LIMIT 1",
                (resultSet, rowNumber) -> resultSet.getString("key"),
                organizationId);
        assertThat(usedKeys).contains("idx_work_items_scope_type_status_page");
    }

    @Test
    void stalePatchFailsWithoutOverwritingTheWinningUpdate() throws Exception {
        JsonNode created = create("REQUIREMENT", "Original", "MEDIUM");
        long id = created.get("id").asLong();

        ResponseEntity<String> first = patch(id, Map.of(
                "title", "Winner", "description", "first", "priority", "HIGH", "expectedVersion", 0));
        ResponseEntity<String> stale = patch(id, Map.of(
                "title", "Stale", "description", "second", "priority", "LOW", "expectedVersion", 0));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(first.getBody()).get("version").asLong()).isEqualTo(1);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).contains("VERSION_CONFLICT");
        JsonNode stored = objectMapper.readTree(get(
                "/api/v1/work-items/" + id,
                ownerCookie).getBody());
        assertThat(stored.get("title").asText()).isEqualTo("Winner");
        assertThat(stored.get("version").asLong()).isEqualTo(1);
    }

    @Test
    void logicalDeletionDoesNotReleaseItsNumber() throws Exception {
        JsonNode first = create("REQUIREMENT", "Disposable", "MEDIUM");
        jdbcTemplate.update("UPDATE work_items SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", first.get("id").asLong());

        JsonNode second = create("REQUIREMENT", "Replacement", "MEDIUM");

        assertThat(second.get("itemKey").asText()).isEqualTo("REQ-2");
        assertThat(get("/api/v1/work-items/" + first.get("id").asLong(), ownerCookie).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void clientSuppliedOrganizationScopeCannotOverrideSessionScope() throws Exception {
        JsonNode item = create("REQUIREMENT", "Private", "MEDIUM");

        assertThat(get("/api/v1/work-items/" + item.get("id").asLong() + "?organizationId=" + (organizationId + 999)
                , ownerCookie).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void typePermissionsAreEnforcedInsideTheApplicationService() {
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE normalized_email = 'owner@example.com'", String.class);
        jdbcTemplate.update(
                "INSERT INTO users (email, normalized_email, display_name, password_hash, status, failed_login_count, "
                        + "locked_until, last_login_at, created_at, updated_at, version) VALUES "
                        + "('developer@example.com', 'developer@example.com', 'Developer', ?, 'ACTIVE', 0, NULL, NULL, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                passwordHash);
        long developerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'developer@example.com'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO organization_members (organization_id, user_id, status, joined_at, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                organizationId,
                developerId);
        jdbcTemplate.update(
                "INSERT INTO member_roles (organization_member_id, role_id, created_at) "
                        + "SELECT wm.id, r.id, UTC_TIMESTAMP(6) FROM organization_members wm CROSS JOIN roles r "
                        + "WHERE wm.organization_id = ? AND wm.user_id = ? AND r.code = 'DEVELOPER'",
                organizationId,
                developerId);
        String developerCookie = login("developer@example.com");

        ResponseEntity<String> requirement = createResponse(
                developerCookie, "REQUIREMENT", "Forbidden requirement", "MEDIUM");
        ResponseEntity<String> devTask = createResponse(developerCookie, "DEV_TASK", "Allowed task", "MEDIUM");

        assertThat(requirement.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(requirement.getBody()).contains("RESOURCE_NOT_FOUND");
        assertThat(devTask.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void rejectsOutOfRangePaginationBeforeQueryingMySql() {
        ResponseEntity<String> response = get("/api/v1/work-items?page=0&pageSize=101", ownerCookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_FAILED");
    }

    @Test
    void fiftyConcurrentCreatesAllocateUniqueMonotonicOrganizationNumbers() throws Exception {
        long ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'owner@example.com'", Long.class);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(50)) {
            List<Future<WorkItem>> futures = IntStream.range(0, 50)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        return commandService.create(
                                ownerId,
                                organizationId,
                                WorkItemType.REQUIREMENT,
                                "Concurrent " + index,
                                "",
                                WorkItemPriority.MEDIUM,
                                null,
                                null);
                    }))
                    .toList();
            start.countDown();
            List<WorkItem> created = futures.stream().map(this::await).toList();

            Set<Long> numbers = created.stream().map(WorkItem::itemNumber).collect(Collectors.toSet());
            Set<String> keys = created.stream().map(WorkItem::itemKey).collect(Collectors.toSet());
            assertThat(numbers).containsExactlyInAnyOrderElementsOf(
                    IntStream.rangeClosed(1, 50).mapToObj(value -> (long) value).toList());
            assertThat(keys).hasSize(50);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT next_value FROM organization_item_sequences WHERE organization_id = ?", Long.class, organizationId))
                    .isEqualTo(51);
        }
    }

    @Test
    void twoConcurrentPatchesWithOneExpectedVersionHaveOneWinner() throws Exception {
        long ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'owner@example.com'", Long.class);
        long workItemId = create("REQUIREMENT", "Before race", "MEDIUM").get("id").asLong();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<WorkItem>> futures = List.of(
                    executor.submit(() -> updateAfter(start, ownerId, workItemId, "First")),
                    executor.submit(() -> updateAfter(start, ownerId, workItemId, "Second")));
            start.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<WorkItem> future : futures) {
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
                            "SELECT version FROM work_items WHERE id = ?", Long.class, workItemId))
                    .isEqualTo(1);
        }
    }

    private WorkItem updateAfter(CountDownLatch start, long ownerId, long workItemId, String title)
            throws InterruptedException {
        start.await();
        return commandService.update(
                ownerId,
                organizationId,
                workItemId,
                title,
                "race",
                WorkItemPriority.HIGH,
                null,
                null,
                0);
    }

    private WorkItem await(Future<WorkItem> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(exception.getCause());
        }
    }

    private JsonNode create(String type, String title, String priority) throws Exception {
        ResponseEntity<String> response = createResponse(ownerCookie, type, title, priority);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> createResponse(String cookie, String type, String title, String priority) {
        return csrf().post(
                "/api/v1/work-items",
                Map.of(
                        "type", type,
                        "title", title,
                        "description", "description",
                        "priority", priority),
                cookie,
                String.class);
    }

    private ResponseEntity<String> patch(long id, Map<String, Object> body) {
        return csrf().patch(
                "/api/v1/work-items/" + id,
                body,
                ownerCookie,
                String.class);
    }

    private String loginOwner() {
        return login("owner@example.com");
    }

    private String login(String email) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "correct-horse-42"),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private ResponseEntity<String> get(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        return csrf().unwrapSuccess(
                restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }
}
