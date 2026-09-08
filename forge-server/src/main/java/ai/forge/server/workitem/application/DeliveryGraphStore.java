package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.List;

public interface DeliveryGraphStore {

    Snapshot load(long workspaceId, long projectId);

    record Snapshot(
            /* 项目内未删除的 Work Item 快照。 */ List<Item> items,
            /* 项目内关系快照。 */ List<Relation> relations,
            /* 项目内未删除的文档快照。 */ List<Document> documents,
            /* 由 Server 事实表投影的外部交付节点及其父边。 */ List<Artifact> artifacts) {}

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

    record Artifact(
            /* 含类型前缀的稳定节点标识。 */ String nodeId,
            /* 关联的 Work Item 或上级交付节点标识。 */ String parentNodeId,
            /* 对应事实表的业务标识。 */ long resourceId,
            /* 前端用于区分交付领域的节点种类。 */ String kind,
            /* 可定位的交付资产类型。 */ String type,
            /* 面向用户的资产标题。 */ String title,
            /* 对应事实的当前状态。 */ String status,
            /* 投影该节点前必须具备的项目权限。 */ String requiredPermission) {}
}
