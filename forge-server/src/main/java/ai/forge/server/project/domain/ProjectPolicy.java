package ai.forge.server.project.domain;

import java.time.Instant;

public record ProjectPolicy(
        /* 策略所属项目。 */ long projectId,
        /* 策略所属工作区。 */ long workspaceId,
        /* 是否允许符合分类条件的 Requirement 跳过 UX。 */ boolean allowSkipUx,
        /* 是否要求 MR 当前 head 的 Pipeline 成功后才能提交 QA。 */ boolean ciRequired,
        /* 最近更新策略的用户。 */ long updatedBy,
        /* 最近更新策略的 UTC 时间。 */ Instant updatedAt,
        /* 策略乐观锁版本。 */ long version) {}
