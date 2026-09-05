package ai.forge.server.gitlab.application;

import ai.forge.server.gitlab.domain.Branch;
import ai.forge.server.gitlab.domain.MergeRequest;

public record DevelopmentResult(
        /* 已关联并持久化的分支标准快照。 */ Branch branch,
        /* 已关联并持久化的 MR 标准快照。 */ MergeRequest mergeRequest,
        /* 是否通过查询远端已有资源完成本次操作。 */ boolean reconciled) {}
