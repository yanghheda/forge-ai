package ai.forge.server.workitem.application;

import java.util.List;

public record DevelopmentQaSummary(
        /* 汇总所属 Requirement。 */ long requirementId,
        /* 项目策略是否强制 MR 当前 head 的 CI 成功。 */ boolean ciRequired,
        /* 项目是否存在 ACTIVE 仓库绑定。 */ boolean repositoryConfigured,
        /* Requirement 直属 Dev Task 及其交付快照。 */ List<DevelopmentQaTask> tasks) {

    public DevelopmentQaSummary {
        tasks = List.copyOf(tasks);
    }

    public static DevelopmentQaSummary empty(long requirementId, boolean ciRequired) {
        return new DevelopmentQaSummary(requirementId, ciRequired, false, List.of());
    }
}
