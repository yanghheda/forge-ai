package ai.forge.server.workitem.domain;

import java.util.List;

public record TransitionDefinition(
        /* 应用该转换定义的工作项类型。 */
        WorkItemType type,
        /* 执行动作前要求的工作项状态。 */
        WorkItemStatus from,
        /* 用户或 Agent 请求执行的工作流动作。 */
        WorkflowAction action,
        /* 动作成功后进入的工作项状态。 */
        WorkItemStatus to,
        /* 执行该动作所需的服务端权限编码。 */
        String requiredPermission,
        /* 状态更新前必须全部通过的确定性准入规则。 */
        List<TransitionGuard> guards) {

    public TransitionDefinition {
        guards = List.copyOf(guards);
    }
}
