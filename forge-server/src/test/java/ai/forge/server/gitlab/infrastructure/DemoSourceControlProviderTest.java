package ai.forge.server.gitlab.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.gitlab.application.DevelopmentContext;
import ai.forge.server.gitlab.application.PipelineContext;
import ai.forge.server.gitlab.application.SourceControlProvider;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DemoSourceControlProviderTest {

    @Test
    void implementsTheProductionSpiWithDeterministicIdempotentResources() {
        SourceControlProvider provider = new DemoSourceControlProvider();
        DevelopmentContext context = context();

        var branch = provider.createBranch(context, "feature/demo-3-phone-login", "base-sha", "start-1");
        var replayedBranch = provider.findBranch(context, branch.name()).orElseThrow();
        var mergeRequest = provider.createMergeRequest(
                context, branch.name(), "main", "手机号验证码登录", "start-1");
        var replayedMergeRequest = provider.findOpenMergeRequest(context, branch.name(), "main").orElseThrow();
        var pipeline = provider.triggerPipeline(
                new PipelineContext(32004, 32042, 32041, "https://gitlab.demo.invalid", "32007",
                        "demo-token"),
                branch.name());

        assertThat(replayedBranch).isEqualTo(branch);
        assertThat(replayedMergeRequest).isEqualTo(mergeRequest);
        assertThat(mergeRequest.state()).isEqualTo("merged");
        assertThat(pipeline.status()).isEqualTo("success");
        assertThat(provider.getJobLog(null, pipeline.remotePipelineId(), 1, 1024))
                .asString()
                .contains("deterministic demo pipeline passed")
                .doesNotContain("demo-token");
    }

    @Test
    void assignsDistinctStableMergeRequestIidsPerBranch() {
        SourceControlProvider provider = new DemoSourceControlProvider();
        DevelopmentContext context = context();
        provider.createBranch(context, "feature/demo-first", "base-sha", "start-1");
        provider.createBranch(context, "feature/demo-second", "base-sha", "start-2");

        var first = provider.createMergeRequest(context, "feature/demo-first", "main", "第一个", "start-1");
        var second = provider.createMergeRequest(context, "feature/demo-second", "main", "第二个", "start-2");

        /* 本地 MR 快照以 (repository_id, remote_mr_iid) 唯一收敛，不同分支必须有不同 IID。 */
        assertThat(first.remoteMrIid()).isNotEqualTo(second.remoteMrIid());
        assertThat(first.webUrl()).contains("/merge_requests/" + first.remoteMrIid());
        /* 同一分支重复调用仍返回同一 IID，保持可断言的确定性。 */
        assertThat(provider.createMergeRequest(context, "feature/demo-first", "main", "第一个", "start-1").remoteMrIid())
                .isEqualTo(first.remoteMrIid());
    }

    private DevelopmentContext context() {
        WorkItem task = new WorkItem(
                32013, 32004, 3, "DEMO-3", WorkItemType.DEV_TASK,
                "手机号验证码登录", "", WorkItemStatus.TODO, WorkItemPriority.HIGH,
                32011L, 32001L, null, 0, Instant.EPOCH, Instant.EPOCH);
        return new DevelopmentContext(
                task, 32011, 32042, 32041, "https://gitlab.demo.invalid", "32007",
                "main", "base-sha", "demo-token", "start-1", false);
    }
}
