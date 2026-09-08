package ai.forge.server.workitem.application;

import java.util.List;

public record WorkItemPage(
        /* 当前页的工作项摘要，按项目内编号倒序排列。 */
        List<WorkItemSummary> items,
        /* 从 1 开始的当前页码。 */
        int page,
        /* 当前请求采用且不超过 100 的分页大小。 */
        int pageSize,
        /* 相同租户、项目和筛选条件下可见记录总数。 */
        long total) {}
