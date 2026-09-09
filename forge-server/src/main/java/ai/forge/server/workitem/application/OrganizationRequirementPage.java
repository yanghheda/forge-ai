package ai.forge.server.workitem.application;

import java.util.List;

public record OrganizationRequirementPage(
        /* 当前页可见需求，按需求编号倒序排列。 */ List<OrganizationRequirementView> items,
        /* 从 1 开始的当前页码。 */ int page,
        /* 当前页最大记录数。 */ int pageSize,
        /* 当前筛选条件下的需求总数。 */ long total) {}
