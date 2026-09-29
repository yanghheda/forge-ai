package ai.forge.server.console.application;

import ai.forge.server.workitem.domain.WorkItemType;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ConsoleModels {
    private ConsoleModels() {}

    public record SearchResult(
            /* 搜索结果的资源类型。 */ String type,
            /* 搜索结果的稳定资源标识。 */ String id,
            /* 搜索结果主标题。 */ String title,
            /* 用于快速识别资源的摘要。 */ String subtitle) {}

    public record BoardItem(
            /* 工作项标识。 */ long id,
            /* 公司内展示编号。 */ String itemKey,
            /* 工作项类型。 */ String type,
            /* 工作项标题。 */ String title,
            /* 看板泳道。 */ String lane,
            /* 泳道内排序位置。 */ long position,
            /* 负责人标识；未分配时为空。 */ Long assigneeUserId,
            /* 任务真实状态的乐观锁版本。 */ long version,
            /* 任务所属需求，用于打开对应阶段工作区。 */ Long requirementId,
            /* 业务处理优先级，取值 URGENT/HIGH/MEDIUM/LOW。 */ String priority,
            /* 期望完成时间；为空表示无截止日期。 */ Instant dueAt,
            /* 执行人显示名；未分配时为空。 */ String assigneeName,
            /* 阻塞原因；未阻塞时为空。 */ String blockedReason,
            /* 当前身份在该任务上可尝试执行的动作；缺陷与不可操作时为空列表。 */ List<WorkflowAction> availableActions,
            /* 动作对应的机器可读缺失材料；无缺失时为空映射。 */ Map<WorkflowAction, List<String>> guardHints) {}

    public record ExecutorOption(
            /* 需求对应领域已关联执行人的用户标识。 */ long userId,
            /* 执行人展示名。 */ String displayName) {}

    public record TaskCreationOption(
            /* 可创建任务的需求工作项标识。 */ long requirementId,
            /* 需求在公司内的展示编号。 */ String requirementItemKey,
            /* 需求标题。 */ String requirementTitle,
            /* 由需求当前准备态唯一决定的任务类型。 */ WorkItemType taskType,
            /* 该任务类型下可被分配的需求执行人。 */ List<ExecutorOption> executors) {}

    public record RequirementOption(
            /* 需求工作项标识。 */ long id,
            /* 公司内展示编号。 */ String itemKey,
            /* 需求标题。 */ String title) {}
}
