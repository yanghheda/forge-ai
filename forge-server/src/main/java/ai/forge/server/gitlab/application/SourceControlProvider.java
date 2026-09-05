package ai.forge.server.gitlab.application;

import ai.forge.server.gitlab.domain.Branch;
import ai.forge.server.gitlab.domain.MergeRequest;
import ai.forge.server.gitlab.domain.PipelineRun;
import java.util.Optional;

public interface SourceControlProvider {

    ConnectionTestResult test(String baseUrl, String token);

    RepositoryDto getRepository(String baseUrl, String token, String remoteProjectId);

    default Optional<Branch> findBranch(DevelopmentContext context, String branchName) {
        throw new UnsupportedOperationException("branch operations are not implemented");
    }

    default Branch createBranch(
            DevelopmentContext context, String branchName, String ref, String idempotencyKey) {
        throw new UnsupportedOperationException("branch operations are not implemented");
    }

    default Optional<MergeRequest> findOpenMergeRequest(
            DevelopmentContext context, String sourceBranch, String targetBranch) {
        throw new UnsupportedOperationException("merge request operations are not implemented");
    }

    default MergeRequest createMergeRequest(
            DevelopmentContext context,
            String sourceBranch,
            String targetBranch,
            String title,
            String idempotencyKey) {
        throw new UnsupportedOperationException("merge request operations are not implemented");
    }

    default PipelineRun triggerPipeline(PipelineContext context, String ref) {
        throw new UnsupportedOperationException("pipeline operations are not implemented");
    }

    default byte[] getJobLog(PipelineContext context, long pipelineId, long jobId, int maxBytes) {
        throw new UnsupportedOperationException("pipeline log operations are not implemented");
    }
}
