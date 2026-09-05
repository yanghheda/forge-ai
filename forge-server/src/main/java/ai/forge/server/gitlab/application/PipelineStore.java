package ai.forge.server.gitlab.application;

import ai.forge.server.gitlab.domain.PipelineRun;
import java.util.List;
import java.util.Optional;

public interface PipelineStore {

    PipelineContext loadContext(long workspaceId, long projectId);

    PipelineRun save(PipelineRun pipeline);

    Optional<PipelineRun> find(long workspaceId, long projectId, long pipelineId);

    List<PipelineRun> list(long workspaceId, long projectId, int limit);
}
