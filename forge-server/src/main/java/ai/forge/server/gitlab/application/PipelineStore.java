package ai.forge.server.gitlab.application;

import ai.forge.server.gitlab.domain.PipelineRun;
import java.util.List;
import java.util.Optional;

public interface PipelineStore {

    PipelineContext loadContext(long organizationId);

    PipelineRun save(PipelineRun pipeline);

    Optional<PipelineRun> find(long organizationId, long pipelineId);

    List<PipelineRun> list(long organizationId, int limit);
}
