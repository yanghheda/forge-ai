package ai.forge.server.release.domain;

import java.util.List;

public record PrecheckFacts(
        /* 被检查 Release Candidate 的乐观锁版本。 */ long releaseVersion,
        /* Release 中 Requirement 的当前状态与版本。 */ List<Item> items,
        /* 各 Requirement 对应 MR head 与最新 Pipeline 事实。 */ List<Pipeline> pipelines,
        /* 各 Requirement 的最新有效 Test Run 事实。 */ List<QaRun> qaRuns,
        /* Release 范围内尚需判断的 Bug 事实。 */ List<Bug> bugs,
        /* Release Note 或引用文档是否存在。 */ boolean artifactsPresent,
        /* 当前项目是否存在可用审批人。 */ boolean approverAvailable,
        /* 策略配置的审批有效期分钟数。 */ long approvalTtlMinutes,
        /* 创建 Release 时固化的策略版本。 */ long policyVersion) {

    public PrecheckFacts {
        items = List.copyOf(items);
        pipelines = List.copyOf(pipelines);
        qaRuns = List.copyOf(qaRuns);
        bugs = List.copyOf(bugs);
    }

    public PrecheckFacts withItems(List<Item> value) {
        return new PrecheckFacts(releaseVersion, value, pipelines, qaRuns, bugs, artifactsPresent,
                approverAvailable, approvalTtlMinutes, policyVersion);
    }

    public PrecheckFacts withPipelines(List<Pipeline> value) {
        return new PrecheckFacts(releaseVersion, items, value, qaRuns, bugs, artifactsPresent,
                approverAvailable, approvalTtlMinutes, policyVersion);
    }

    public PrecheckFacts withQaRuns(List<QaRun> value) {
        return new PrecheckFacts(releaseVersion, items, pipelines, value, bugs, artifactsPresent,
                approverAvailable, approvalTtlMinutes, policyVersion);
    }

    public PrecheckFacts withBugs(List<Bug> value) {
        return new PrecheckFacts(releaseVersion, items, pipelines, qaRuns, value, artifactsPresent,
                approverAvailable, approvalTtlMinutes, policyVersion);
    }

    public PrecheckFacts withArtifacts(boolean value) {
        return new PrecheckFacts(releaseVersion, items, pipelines, qaRuns, bugs, value,
                approverAvailable, approvalTtlMinutes, policyVersion);
    }

    public PrecheckFacts withPolicy(boolean available, long ttlMinutes) {
        return new PrecheckFacts(releaseVersion, items, pipelines, qaRuns, bugs, artifactsPresent,
                available, ttlMinutes, policyVersion);
    }

    public record Item(
            /* Requirement 可读 key。 */ String key,
            /* Requirement 当前状态。 */ String status,
            /* Requirement 当前乐观锁版本。 */ long version) {}

    public record Pipeline(
            /* Pipeline 所属 Requirement key。 */ String itemKey,
            /* 对应 Merge Request 可读引用。 */ String mergeRequestRef,
            /* Merge Request 当前 head SHA。 */ String headSha,
            /* 最新 Pipeline 的 commit SHA；不存在时为空。 */ String pipelineSha,
            /* 最新 Pipeline 状态；不存在时为空。 */ String status,
            /* Pipeline 资源标识，用作版本快照。 */ long version) {}

    public record QaRun(
            /* Test Run 所属 Requirement key。 */ String itemKey,
            /* 最新 Test Run 标识；不存在时为零。 */ long runId,
            /* 最新 Test Run 状态；不存在时为空。 */ String status,
            /* 固化统计中的通过数。 */ int passed,
            /* 固化统计中的失败数。 */ int failed,
            /* 固化统计中的阻塞数。 */ int blocked,
            /* Test Run 乐观锁版本。 */ long version) {}

    public record Bug(
            /* Bug 可读 key。 */ String key,
            /* Bug 严重级别。 */ String severity,
            /* Bug 当前状态。 */ String status,
            /* Bug 乐观锁版本。 */ long version) {}
}
