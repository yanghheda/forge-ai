package ai.forge.server.workitem.application;

public record RequirementOverview(
        /* 当前组织全部未删除 Requirement 数量。 */ long total,
        /* 尚未到达 DONE 或 RELEASED 的 Requirement 数量。 */ long inProgress,
        /* 已到达 DONE 或 RELEASED 的 Requirement 数量。 */ long completed) {}
