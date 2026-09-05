package ai.forge.server.gitlab.application;

import ai.forge.server.workitem.domain.WorkItem;

public record DevelopmentContext(
        /* 本次启动的 Dev Task。 */ WorkItem task,
        /* Dev Task 所属 READY_FOR_DEV Requirement。 */ long requirementId,
        /* 项目当前 ACTIVE 仓库绑定标识。 */ long repositoryId,
        /* 仓库使用的 GitLab 连接标识。 */ long connectionId,
        /* 已通过 URL 策略的 GitLab 根地址。 */ String baseUrl,
        /* GitLab 不可变项目标识。 */ String remoteProjectId,
        /* 仓库绑定记录的默认目标分支。 */ String defaultBranch,
        /* 创建新分支使用且已从远端解析的基准 SHA。 */ String baseSha,
        /* 仅在当前调用栈短暂存在的 GitLab Token。 */ String token,
        /* 定位本地外部写意图的项目范围幂等键。 */ String idempotencyKey,
        /* 相同幂等键已经完成时为真。 */ boolean completed) {

    @Override
    public String toString() {
        return "DevelopmentContext[task=" + task.id() + ", repositoryId=" + repositoryId
                + ", remoteProjectId=" + remoteProjectId + ", completed=" + completed + "]";
    }
}
