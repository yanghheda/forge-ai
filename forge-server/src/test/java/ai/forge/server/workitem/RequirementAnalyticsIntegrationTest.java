package ai.forge.server.workitem;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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

/* 通过真实 Controller 与 MySQL 事实验证全局仪表盘聚合口径。 */
class RequirementAnalyticsIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 Session 调用聚合接口的客户端。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析聚合响应与稳定错误体。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 直接搭建需求、事件与租户事实，避免依赖尚未开放的业务写入入口。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String ownerCookie;
    private long ownerId;
    private long organizationId;
    private long itemNumber;

    @BeforeEach
    void initializeCompany() throws Exception {
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
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
                "organization_policies", "audit_logs", "member_roles", "organization_members", "organizations",
                "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        ResponseEntity<String> initialized = csrf().post(
                "/api/v1/setup/initialize",
                java.util.Map.of(
                        "adminEmail", ownerEmail,
                        "adminDisplayName", "Forge Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge"),
                null,
                String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ownerCookie = login(ownerEmail);
        ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = ?", Long.class, ownerEmail);
        organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1", Long.class);
        itemNumber = 0;
    }

    @Test
    void emptyCompanyReturnsZeroMetricsNullRatesAndTheFullFunnelOrder() throws Exception {
        JsonNode analytics = analytics(30);
        JsonNode metrics = analytics.get("metrics");

        assertThat(analytics.get("rangeDays").asInt()).isEqualTo(30);
        assertThat(metrics.get("total").asLong()).isZero();
        assertThat(metrics.get("inProgress").asLong()).isZero();
        assertThat(metrics.get("completed").asLong()).isZero();
        assertThat(metrics.get("overdue").asLong()).isZero();
        assertThat(metrics.get("createdInRange").asLong()).isZero();
        assertThat(metrics.get("completedInRange").asLong()).isZero();
        assertThat(metrics.get("onTimeRate").isNull()).isTrue();
        assertThat(metrics.get("avgCycleDays").isNull()).isTrue();
        assertThat(metrics.get("createdDeltaRate").isNull()).isTrue();
        assertThat(metrics.get("completedDeltaRate").isNull()).isTrue();

        JsonNode statusFunnel = analytics.get("statusFunnel");
        assertThat(statusFunnel).hasSize(13);
        assertThat(statusFunnel).extracting(bucket -> bucket.get("status").asText())
                .containsExactly(
                        "DRAFT", "PRODUCT_REVIEW", "UX_IN_PROGRESS", "UX_REVIEW", "READY_FOR_DEV",
                        "IN_DEVELOPMENT", "READY_FOR_QA", "IN_QA", "READY_FOR_RELEASE", "RELEASED",
                        "DONE", "REJECTED", "CANCELLED");
        assertThat(statusFunnel).allSatisfy(bucket -> assertThat(bucket.get("total").asLong()).isZero());
    }

    @Test
    void distributionsCountOnlyRequirementsAndKeepStableOrder() throws Exception {
        insertRequirement("DRAFT", "URGENT", utcDate(0, 12), null);
        insertRequirement("IN_DEVELOPMENT", "HIGH", utcDate(0, 12), utcDate(-5, 12));
        insertRequirement("DONE", "MEDIUM", utcDate(0, 12), null);
        insertRequirement("IN_QA", "LOW", utcDate(0, 12), null);
        insertRequirement("RELEASED", "HIGH", utcDate(0, 12), null);
        insertWorkItem("BUG", "IN_DEVELOPMENT", "URGENT", utcDate(0, 12), null);

        JsonNode analytics = analytics(30);
        JsonNode metrics = analytics.get("metrics");
        assertThat(metrics.get("total").asLong()).isEqualTo(5);
        assertThat(metrics.get("inProgress").asLong()).isEqualTo(3);
        assertThat(metrics.get("completed").asLong()).isEqualTo(2);

        JsonNode statusFunnel = analytics.get("statusFunnel");
        assertThat(funnelCount(statusFunnel, "DRAFT")).isEqualTo(1);
        assertThat(funnelCount(statusFunnel, "IN_DEVELOPMENT")).isEqualTo(1);
        assertThat(funnelCount(statusFunnel, "IN_QA")).isEqualTo(1);
        assertThat(funnelCount(statusFunnel, "DONE")).isEqualTo(1);
        assertThat(funnelCount(statusFunnel, "RELEASED")).isEqualTo(1);
        assertThat(funnelCount(statusFunnel, "PRODUCT_REVIEW")).isZero();
        assertThat(funnelCount(statusFunnel, "REJECTED")).isZero();

        JsonNode priorityDistribution = analytics.get("priorityDistribution");
        assertThat(priorityDistribution).extracting(bucket -> bucket.get("priority").asText())
                .containsExactly("URGENT", "HIGH", "MEDIUM", "LOW");
        assertThat(priorityCount(priorityDistribution, "URGENT")).isEqualTo(1);
        assertThat(priorityCount(priorityDistribution, "HIGH")).isEqualTo(2);
        assertThat(priorityCount(priorityDistribution, "MEDIUM")).isEqualTo(1);
        assertThat(priorityCount(priorityDistribution, "LOW")).isEqualTo(1);
    }

    @Test
    void completionIsCountedOnceOnTheFirstTerminalEvent() throws Exception {
        long requirementId = insertRequirement("RELEASED", "HIGH", utcDate(20, 12), null);
        insertEvent(requirementId, "RELEASED", utcDate(5, 12), "complete-released");
        insertEvent(requirementId, "DONE", utcDate(3, 12), "complete-done");

        JsonNode analytics = analytics(30);
        JsonNode flowTrend = analytics.get("flowTrend");
        assertThat(flowCount(flowTrend, "completed", utcDay(5))).isEqualTo(1);
        assertThat(flowCount(flowTrend, "completed", utcDay(3))).isZero();
        assertThat(analytics.get("metrics").get("completedInRange").asLong()).isEqualTo(1);
    }

    @Test
    void backlogTrendCarriesTheBaselineAndAppliesCreatedMinusCompleted() throws Exception {
        insertRequirement("IN_DEVELOPMENT", "HIGH", utcDate(400, 12), null);
        long completedLater = insertRequirement("RELEASED", "MEDIUM", utcDate(10, 12), null);
        insertEvent(completedLater, "RELEASED", utcDate(5, 12), "backlog-complete");

        JsonNode analytics = analytics(30);
        JsonNode backlogTrend = analytics.get("backlogTrend");
        JsonNode flowTrend = analytics.get("flowTrend");

        assertThat(backlogTrend).hasSize(30);
        assertThat(backlogTrend.get(0).get("value").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(flowCount(flowTrend, "created", utcDay(10))).isEqualTo(1);
        assertThat(flowCount(flowTrend, "completed", utcDay(5))).isEqualTo(1);
        assertThat(backlogValue(backlogTrend, utcDay(10))).isEqualTo(2);
        assertThat(backlogValue(backlogTrend, utcDay(5))).isEqualTo(1);
        for (int index = 1; index < backlogTrend.size(); index++) {
            long expected = backlogTrend.get(index - 1).get("value").asLong()
                    + flowTrend.get(index).get("created").asLong()
                    - flowTrend.get(index).get("completed").asLong();
            assertThat(backlogTrend.get(index).get("value").asLong()).isEqualTo(expected);
        }
    }

    @Test
    void overdueRequirementAppearsInRisksWhileHealthyOneDoesNot() throws Exception {
        long overdueId = insertRequirement("IN_DEVELOPMENT", "HIGH", utcDate(20, 12), utcDate(1, 0));
        String overdueKey = jdbcTemplate.queryForObject(
                "SELECT item_key FROM work_items WHERE id = ?", String.class, overdueId);
        long healthyId = insertRequirement("IN_DEVELOPMENT", "MEDIUM", utcDate(20, 12), utcDate(-5, 12));
        String healthyKey = jdbcTemplate.queryForObject(
                "SELECT item_key FROM work_items WHERE id = ?", String.class, healthyId);

        JsonNode analytics = analytics(30);
        assertThat(analytics.get("metrics").get("overdue").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode risks = analytics.get("risks");
        assertThat(risks).extracting(risk -> risk.get("itemKey").asText()).contains(overdueKey);
        assertThat(risks).extracting(risk -> risk.get("itemKey").asText()).doesNotContain(healthyKey);
    }

    @Test
    void anotherTenantRequirementIsExcludedFromEveryStatistic() throws Exception {
        insertRequirement("DRAFT", "HIGH", utcDate(0, 12), null);
        jdbcTemplate.update(
                "INSERT INTO organizations (name,slug,owner_user_id,created_at,updated_at,version) "
                        + "VALUES ('Other','other-org',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                ownerId);
        long otherOrganizationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO work_items (organization_id,item_number,item_key,type,title,description,status,"
                        + "priority,reporter_user_id,created_at,updated_at,version) "
                        + "VALUES (?,1,'OTHER-1','REQUIREMENT','Other tenant requirement','','DRAFT','HIGH',?,"
                        + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                otherOrganizationId,
                ownerId);

        JsonNode analytics = analytics(30);
        assertThat(analytics.get("metrics").get("total").asLong()).isEqualTo(1);
        assertThat(funnelCount(analytics.get("statusFunnel"), "DRAFT")).isEqualTo(1);
    }

    @Test
    void rangeOutsideAllowedBoundsIsRejected() {
        assertThat(get("/api/v1/requirements/analytics?range=0").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/requirements/analytics?range=999").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /* 以 UTC 日期文本表达时间事实，避免 JDBC 会话时区换算影响 DATE(created_at) 口径。 */
    private static String utcDate(int daysAgo, int hour) {
        return utcDay(daysAgo) + " " + String.format("%02d:00:00", hour);
    }

    /* 趋势序列的 date 字段为纯 UTC 日期文本。 */
    private static String utcDay(int daysAgo) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo).toString();
    }

    private long insertRequirement(String status, String priority, String createdAt, String dueAt) {
        return insertWorkItem("REQUIREMENT", status, priority, createdAt, dueAt);
    }

    private long insertWorkItem(String type, String status, String priority, String createdAt, String dueAt) {
        itemNumber++;
        String itemKey = "FORGE-" + itemNumber;
        jdbcTemplate.update(
                "INSERT INTO work_items (organization_id,item_number,item_key,type,title,description,status,"
                        + "priority,reporter_user_id,due_at,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,'',?,?,?,?,?,?,0)",
                organizationId,
                itemNumber,
                itemKey,
                type,
                "Work item " + itemKey,
                status,
                priority,
                ownerId,
                dueAt,
                createdAt,
                createdAt);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /* 追加写入终态事件，驱动完成趋势与燃尽口径。 */
    private void insertEvent(long workItemId, String toStatus, String createdAt, String idempotencyKey) {
        jdbcTemplate.update(
                "INSERT INTO work_item_events (organization_id,work_item_id,event_type,from_status,to_status,"
                        + "actor_type,actor_id,metadata_json,idempotency_key,created_at) "
                        + "VALUES (?,?,?,?,?,'USER',?,JSON_OBJECT(),?,?)",
                organizationId,
                workItemId,
                "COMPLETE_" + toStatus,
                "IN_QA",
                toStatus,
                ownerId,
                idempotencyKey,
                createdAt);
    }

    private JsonNode analytics(int range) throws Exception {
        ResponseEntity<String> response = get("/api/v1/requirements/analytics?range=" + range);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private static long funnelCount(JsonNode statusFunnel, String status) {
        for (JsonNode bucket : statusFunnel) {
            if (bucket.get("status").asText().equals(status)) {
                return bucket.get("total").asLong();
            }
        }
        throw new AssertionError("status not found: " + status);
    }

    private static long priorityCount(JsonNode priorityDistribution, String priority) {
        for (JsonNode bucket : priorityDistribution) {
            if (bucket.get("priority").asText().equals(priority)) {
                return bucket.get("total").asLong();
            }
        }
        throw new AssertionError("priority not found: " + priority);
    }

    private static long flowCount(JsonNode flowTrend, String field, String date) {
        return flowTrend.get(indexOfDate(flowTrend, date)).get(field).asLong();
    }

    private static long backlogValue(JsonNode backlogTrend, String date) {
        return backlogTrend.get(indexOfDate(backlogTrend, date)).get("value").asLong();
    }

    /* 在趋势序列中定位 UTC 日期，避免硬编码窗口内的偏移下标。 */
    private static int indexOfDate(JsonNode series, String date) {
        for (int index = 0; index < series.size(); index++) {
            if (series.get(index).get("date").asText().equals(date)) {
                return index;
            }
        }
        throw new AssertionError("date not found: " + date);
    }

    private String login(String email) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login",
                java.util.Map.of("email", email, "password", "correct-horse-42"),
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
