package ai.forge.server.workitem.domain;

import java.time.Instant;

public record WorkItemRelation(
        /* 关系的稳定标识。 */ long id,
        /* 关系起点工作项。 */ long sourceId,
        /* 关系终点工作项。 */ long targetId,
        /* 有向关系的业务语义。 */ WorkItemRelationType relationType,
        /* 创建关系的用户。 */ long createdBy,
        /* 关系创建的 UTC 时间。 */ Instant createdAt) {}
