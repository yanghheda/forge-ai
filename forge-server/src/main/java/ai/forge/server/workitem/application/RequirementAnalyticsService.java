package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.organization.domain.OrganizationContext;
import ai.forge.server.workitem.domain.WorkItemPriority;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class RequirementAnalyticsService {

    /* 允许的最大统计窗口天数，避免单次请求扫描过长历史。 */
    public static final int MAX_RANGE_DAYS = 180;

    /* Requirement 生命周期状态按流程推进顺序的显式序列，用于状态漏斗的稳定展示顺序。 */
    private static final List<String> FUNNEL_STATUSES = List.of(
            "DRAFT", "PRODUCT_REVIEW", "UX_IN_PROGRESS", "UX_REVIEW", "READY_FOR_DEV",
            "IN_DEVELOPMENT", "READY_FOR_QA", "IN_QA", "READY_FOR_RELEASE", "RELEASED",
            "DONE", "REJECTED", "CANCELLED");

    /* 视为已终结的 Requirement 状态；不参与进行中统计。 */
    private static final List<String> TERMINAL_STATUSES = List.of("DONE", "RELEASED", "CANCELLED", "REJECTED");

    /* 风险清单单次返回的最大条数。 */
    private static final int RISK_LIMIT = 8;

    /* 解析当前用户唯一公司上下文。 */
    private final OrganizationAccessService accessService;

    /* 执行公司级需求读取权限校验。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 执行需求、缺陷与测试结果的聚合查询。 */
    private final RequirementAnalyticsStore store;

    public RequirementAnalyticsService(
            OrganizationAccessService accessService,
            PermissionEvaluator permissionEvaluator,
            RequirementAnalyticsStore store) {
        this.accessService = accessService;
        this.permissionEvaluator = permissionEvaluator;
        this.store = store;
    }

    public RequirementAnalyticsModels.Analytics analytics(long userId, int rangeDays) {
        if (rangeDays < 1 || rangeDays > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("range must be 1.." + MAX_RANGE_DAYS);
        }
        OrganizationContext scope = accessService.requireContext(userId);
        long organizationId = scope.organizationId();
        permissionEvaluator.requireOrganization(userId, organizationId, "requirement.read");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate windowStartDate = today.minusDays(rangeDays - 1L);
        LocalDateTime windowStart = windowStartDate.atStartOfDay();
        // 环比需要与当前窗口等长的上一个窗口，因此一次取两倍天数再切分。
        int extendedDaysBack = rangeDays * 2 - 1;

        Map<String, Long> createdByDay = byDay(store.dailyCreated(organizationId, extendedDaysBack));
        Map<String, Long> completedByDay = byDay(store.dailyCompleted(organizationId, extendedDaysBack));

        List<RequirementAnalyticsModels.DailyFlow> flowTrend = new ArrayList<>();
        List<RequirementAnalyticsModels.BacklogPoint> backlogTrend = new ArrayList<>();
        long backlog = store.backlogBaseline(organizationId, windowStart);
        long createdInRange = 0;
        long completedInRange = 0;
        for (int offset = 0; offset < rangeDays; offset++) {
            String day = windowStartDate.plusDays(offset).toString();
            long created = createdByDay.getOrDefault(day, 0L);
            long completed = completedByDay.getOrDefault(day, 0L);
            createdInRange += created;
            completedInRange += completed;
            backlog += created - completed;
            flowTrend.add(new RequirementAnalyticsModels.DailyFlow(day, created, completed));
            backlogTrend.add(new RequirementAnalyticsModels.BacklogPoint(day, backlog));
        }

        long previousCreated = 0;
        long previousCompleted = 0;
        for (int offset = 0; offset < rangeDays; offset++) {
            String day = windowStartDate.minusDays(rangeDays - offset).toString();
            previousCreated += createdByDay.getOrDefault(day, 0L);
            previousCompleted += completedByDay.getOrDefault(day, 0L);
        }

        Map<String, Long> statusCounts = counts(store.statusDistribution(organizationId), "status");
        long total = 0;
        long inProgress = 0;
        long completed = 0;
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            total += entry.getValue();
            if ("DONE".equals(entry.getKey()) || "RELEASED".equals(entry.getKey())) {
                completed += entry.getValue();
            } else if (!TERMINAL_STATUSES.contains(entry.getKey())) {
                inProgress += entry.getValue();
            }
        }

        Map<String, Object> quality = store.completionQuality(organizationId, windowStart);
        long dueTotal = number(quality, "due_total");
        long dueOnTime = number(quality, "due_on_time");

        Map<String, Long> priorityCounts = counts(store.priorityDistribution(organizationId), "priority");

        Map<String, Object> qa = store.qaQuality(organizationId, windowStart);
        long passed = number(qa, "passed");
        long failed = number(qa, "failed");
        long blocked = number(qa, "blocked");
        long executed = passed + failed + blocked;

        RequirementAnalyticsModels.Metrics metrics = new RequirementAnalyticsModels.Metrics(
                total,
                inProgress,
                completed,
                store.overdue(organizationId),
                createdInRange,
                completedInRange,
                dueTotal == 0 ? null : (double) dueOnTime / dueTotal,
                decimal(quality, "avg_cycle_days"),
                rate(createdInRange, previousCreated),
                rate(completedInRange, previousCompleted));

        List<RequirementAnalyticsModels.StatusBucket> statusFunnel = FUNNEL_STATUSES.stream()
                .map(status -> new RequirementAnalyticsModels.StatusBucket(
                        status, statusCounts.getOrDefault(status, 0L)))
                .toList();

        List<RequirementAnalyticsModels.PriorityBucket> priorityDistribution = List.of(
                        WorkItemPriority.URGENT, WorkItemPriority.HIGH,
                        WorkItemPriority.MEDIUM, WorkItemPriority.LOW)
                .stream()
                .map(priority -> new RequirementAnalyticsModels.PriorityBucket(
                        priority.name(), priorityCounts.getOrDefault(priority.name(), 0L)))
                .toList();

        List<RequirementAnalyticsModels.BugBucket> bugDistribution = store.bugDistribution(organizationId).stream()
                .map(row -> new RequirementAnalyticsModels.BugBucket(
                        text(row, "severity"), text(row, "status"), number(row, "total")))
                .toList();

        RequirementAnalyticsModels.QaQuality qaQuality = new RequirementAnalyticsModels.QaQuality(
                passed,
                failed,
                blocked,
                number(qa, "skipped"),
                number(qa, "not_run"),
                executed == 0 ? null : (double) passed / executed);

        List<RequirementAnalyticsModels.RiskItem> risks = store.risks(organizationId, RISK_LIMIT).stream()
                .map(row -> new RequirementAnalyticsModels.RiskItem(
                        number(row, "id"),
                        text(row, "item_key"),
                        text(row, "title"),
                        text(row, "status"),
                        text(row, "priority"),
                        instant(row.get("due_at")),
                        row.get("blocked_at") != null))
                .toList();

        return new RequirementAnalyticsModels.Analytics(
                rangeDays,
                metrics,
                statusFunnel,
                priorityDistribution,
                List.copyOf(flowTrend),
                List.copyOf(backlogTrend),
                bugDistribution,
                qaQuality,
                risks);
    }

    private static Double rate(long current, long previous) {
        return previous == 0 ? null : (double) (current - previous) / previous;
    }

    private static Map<String, Long> byDay(List<Map<String, Object>> rows) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            values.put(row.get("day").toString(), number(row, "total"));
        }
        return values;
    }

    private static Map<String, Long> counts(List<Map<String, Object>> rows, String key) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            values.put(row.get(key).toString(), number(row, "total"));
        }
        return values;
    }

    private static long number(Map<String, Object> row, String key) {
        if (row == null) {
            return 0L;
        }
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static Double decimal(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object value = row.get(key);
        return value == null ? null : ((Number) value).doubleValue();
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static Instant instant(Object value) {
        return value == null ? null : ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }
}
