package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.List;

public interface DeliveryGraphStore {

    Snapshot load(long workspaceId, long projectId);

    record Snapshot(
            /* 项目内未删除的 Work Item 快照。 */ List<Item> items,
            /* 项目内关系快照。 */ List<Relation> relations,
            /* 项目内未删除的文档快照。 */ List<Document> documents) {}

    record Item(
            /* Work Item 标识。 */ long id,
            /* 可选父工作项标识。 */ Long parentId,
            /* 决定节点授权的工作项类型。 */ WorkItemType type,
            /* 项目内展示编号。 */ String itemKey,
            /* 工作项标题。 */ String title,
            /* 当前工作流状态。 */ WorkItemStatus status) {}

    record Relation(
            /* 关系标识。 */ long id,
            /* 关系起点。 */ long sourceId,
            /* 关系终点。 */ long targetId,
            /* 固定关系语义。 */ String type) {}

    record Document(
            /* 文档标识。 */ long id,
            /* 文档所属工作项。 */ long workItemId,
            /* 文档类型。 */ String type,
            /* 文档标题。 */ String title,
            /* 文档状态。 */ String status) {}
}
