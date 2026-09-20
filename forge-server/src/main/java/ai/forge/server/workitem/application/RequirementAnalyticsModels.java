package ai.forge.server.workitem.application;

import java.time.Instant;
import java.util.List;

/* 全局仪表盘的只读聚合视图；所有序列以同一统计窗口为准，空样本用 null 表达而非 0。 */
public final class RequirementAnalyticsModels {
    private RequirementAnalyticsModels() {}

    public record Analytics(
            /* 统计窗口天数；所有趋势序列均覆盖该窗口。 */
            int rangeDays,
            /* 顶部核心指标。 */
            Metrics metrics,
            /* 需求在各生命周期状态上的当前分布，按流程推进顺序返回。 */
            List<StatusBucket> statusFunnel,
            /* 需求在各业务优先级上的当前分布，按紧迫度降序返回。 */
            List<PriorityBucket> priorityDistribution,
            /* 窗口内每日新建与完成数量。 */
            List<DailyFlow> flowTrend,
            /* 窗口内每日日终的未完成存量，用于观察积压变化。 */
            List<BacklogPoint> backlogTrend,
            /* 当前未删除缺陷按严重级别与状态聚合的分布快照。 */
            List<BugBucket> bugDistribution,
            /* 窗口内测试执行结果汇总。 */
            QaQuality qaQuality,
            /* 需要关注的风险需求；超期或已阻塞，按风险程度降序。 */
            List<RiskItem> risks) {}

    public record Metrics(
            /* 当前未删除需求总数。 */
            long total,
            /* 当前处于非终结状态的需求数。 */
            long inProgress,
            /* 当前处于已发布或已完成状态的需求数。 */
            long completed,
            /* 当前已超过期望完成时间且未终结的需求数。 */
            long overdue,
            /* 窗口内新建需求数。 */
            long createdInRange,
            /* 窗口内完成需求数。 */
            long completedInRange,
            /* 窗口内完成且设置过期望时间的需求中按期完成的比例；无样本时为空。 */
            Double onTimeRate,
            /* 窗口内完成需求从创建到首次进入终态的平均天数；无样本时为空。 */
            Double avgCycleDays,
            /* 窗口新建数相对上一个等长窗口的变化率；上期为 0 时为空。 */
            Double createdDeltaRate,
            /* 窗口完成数相对上一个等长窗口的变化率；上期为 0 时为空。 */
            Double completedDeltaRate) {}

    public record StatusBucket(
            /* 需求生命周期状态代码。 */
            String status,
            /* 当前处于该状态的需求数。 */
            long total) {}

    public record PriorityBucket(
            /* 业务优先级代码。 */
            String priority,
            /* 当前处于该优先级的需求数。 */
            long total) {}

    public record DailyFlow(
            /* UTC 日期文本。 */
            String date,
            /* 当日新建需求数。 */
            long created,
            /* 当日完成需求数。 */
            long completed) {}

    public record BacklogPoint(
            /* UTC 日期文本。 */
            String date,
            /* 当日日终仍未完成的需求存量。 */
            long value) {}

    public record BugBucket(
            /* 缺陷严重级别；历史数据未设置时为空字符串。 */
            String severity,
            /* 缺陷当前生命周期状态。 */
            String status,
            /* 处于该严重级别与状态组合的缺陷数。 */
            long total) {}

    public record QaQuality(
            /* 窗口内通过的测试结果数。 */
            long passed,
            /* 窗口内失败的测试结果数。 */
            long failed,
            /* 窗口内被阻塞的测试结果数。 */
            long blocked,
            /* 窗口内被跳过的测试结果数。 */
            long skipped,
            /* 窗口内尚未执行的测试结果数。 */
            long notRun,
            /* 执行结果通过率，分母为通过、失败与阻塞之和；无样本时为空。 */
            Double passRate) {}

    public record RiskItem(
            /* 需求工作项标识。 */
            long id,
            /* 公司内展示编号。 */
            String itemKey,
            /* 需求标题。 */
            String title,
            /* 需求当前生命周期状态。 */
            String status,
            /* 需求业务优先级。 */
            String priority,
            /* 期望完成的 UTC 时间；未设置时为空。 */
            Instant dueAt,
            /* 是否处于阻塞状态。 */
            boolean blocked) {}
}
