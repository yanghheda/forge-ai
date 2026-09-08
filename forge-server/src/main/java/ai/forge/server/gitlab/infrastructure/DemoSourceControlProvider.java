package ai.forge.server.gitlab.infrastructure;

import ai.forge.server.gitlab.application.ConnectionTestResult;
import ai.forge.server.gitlab.application.DevelopmentContext;
import ai.forge.server.gitlab.application.PipelineContext;
import ai.forge.server.gitlab.application.RepositoryDto;
import ai.forge.server.gitlab.application.SourceControlProvider;
import ai.forge.server.gitlab.domain.Branch;
import ai.forge.server.gitlab.domain.MergeRequest;
import ai.forge.server.gitlab.domain.PipelineRun;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "forge.gitlab.provider", havingValue = "demo")
public class DemoSourceControlProvider implements SourceControlProvider {

    /* Demo 进程内已创建分支；键同时包含仓库，避免项目间资源串用。 */
    private final Map<String, Branch> branches = new ConcurrentHashMap<>();

    /* Demo 进程内已创建 MR；相同源/目标分支重复调用返回同一资源。 */
    private final Map<String, MergeRequest> mergeRequests = new ConcurrentHashMap<>();

    /* 所有确定性响应使用的固定观察时间，防止截图与断言随运行时间漂移。 */
    private static final LocalDateTime OBSERVED_AT = LocalDateTime.of(2026, 1, 1, 9, 10);

    @Override
    public ConnectionTestResult test(String baseUrl, String token) {
        return new ConnectionTestResult("32001", "demo-gitlab-user");
    }

    @Override
    public RepositoryDto getRepository(String baseUrl, String token, String remoteProjectId) {
        return new RepositoryDto(
                remoteProjectId,
                "demo/demo-shop",
                "https://gitlab.demo.invalid/demo/demo-shop",
                "main");
    }

    @Override
    public Optional<Branch> findBranch(DevelopmentContext context, String branchName) {
        if (branchName.equals(context.defaultBranch())) {
            return Optional.of(branch(context, branchName, context.baseSha()));
        }
        return Optional.ofNullable(branches.get(branchKey(context.repositoryId(), branchName)));
    }

    @Override
    public Branch createBranch(
            DevelopmentContext context, String branchName, String ref, String idempotencyKey) {
        return branches.computeIfAbsent(
                branchKey(context.repositoryId(), branchName),
                ignored -> branch(context, branchName, ref));
    }

    @Override
    public Optional<MergeRequest> findOpenMergeRequest(
            DevelopmentContext context, String sourceBranch, String targetBranch) {
        return Optional.ofNullable(mergeRequests.get(mergeRequestKey(
                context.repositoryId(), sourceBranch, targetBranch)));
    }

    @Override
    public MergeRequest createMergeRequest(
            DevelopmentContext context,
            String sourceBranch,
            String targetBranch,
            String title,
            String idempotencyKey) {
        return mergeRequests.computeIfAbsent(
                mergeRequestKey(context.repositoryId(), sourceBranch, targetBranch),
                ignored -> new MergeRequest(
                        0,
                        context.task().workspaceId(),
                        context.repositoryId(),
                        context.task().id(),
                        18,
                        title,
                        sourceBranch,
                        targetBranch,
                        "opened",
                        "https://gitlab.demo.invalid/demo/demo-shop/-/merge_requests/18",
                        "32001",
                        requireBranch(context.repositoryId(), sourceBranch).commitSha(),
                        "can_be_merged",
                        OBSERVED_AT,
                        OBSERVED_AT,
                        0));
    }

    @Override
    public PipelineRun triggerPipeline(PipelineContext context, String ref) {
        return new PipelineRun(
                0,
                context.workspaceId(),
                context.repositoryId(),
                null,
                801,
                ref,
                findCommit(context.repositoryId(), ref),
                "success",
                "https://gitlab.demo.invalid/demo/demo-shop/-/pipelines/801",
                OBSERVED_AT.minusMinutes(4),
                OBSERVED_AT,
                OBSERVED_AT,
                OBSERVED_AT,
                "{\"passed\":12,\"failed\":0}");
    }

    @Override
    public byte[] getJobLog(PipelineContext context, long pipelineId, long jobId, int maxBytes) {
        byte[] log = "deterministic demo pipeline passed\n".getBytes(StandardCharsets.UTF_8);
        return java.util.Arrays.copyOf(log, Math.min(log.length, Math.max(0, maxBytes)));
    }

    private Branch branch(DevelopmentContext context, String name, String commitSha) {
        return new Branch(
                0,
                context.task().workspaceId(),
                context.repositoryId(),
                context.task().id(),
                name,
                commitSha == null || commitSha.isBlank() ? "demo-base-sha" : commitSha,
                "ACTIVE",
                OBSERVED_AT,
                OBSERVED_AT);
    }

    private Branch requireBranch(long repositoryId, String name) {
        Branch branch = branches.get(branchKey(repositoryId, name));
        if (branch == null) {
            throw new IllegalStateException("demo branch must be created before merge request");
        }
        return branch;
    }

    private String findCommit(long repositoryId, String ref) {
        Branch branch = branches.get(branchKey(repositoryId, ref));
        return branch == null ? "demo-pipeline-sha" : branch.commitSha();
    }

    private String branchKey(long repositoryId, String branchName) {
        return repositoryId + ":" + branchName;
    }

    private String mergeRequestKey(long repositoryId, String sourceBranch, String targetBranch) {
        return repositoryId + ":" + sourceBranch + ":" + targetBranch;
    }
}
