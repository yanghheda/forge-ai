package ai.forge.server.qa.domain;

import ai.forge.server.workitem.domain.WorkItemStatus;

public record BugTransition(
        /* 动作执行前必须匹配的 Bug 状态。 */ WorkItemStatus from,
        /* 用户请求的固定 Bug 动作。 */ BugAction action,
        /* 动作成功后写入的 Bug 状态。 */ WorkItemStatus to,
        /* 应用服务执行迁移前检查的项目权限。 */ String requiredPermission) {}
