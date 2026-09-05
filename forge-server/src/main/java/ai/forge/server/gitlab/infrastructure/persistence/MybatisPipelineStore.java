package ai.forge.server.gitlab.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.gitlab.application.EncryptedSecret;
import ai.forge.server.gitlab.application.PipelineContext;
import ai.forge.server.gitlab.application.PipelineStore;
import ai.forge.server.gitlab.application.SecretService;
import ai.forge.server.gitlab.domain.PipelineRun;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisPipelineStore implements PipelineStore {

    /* Pipeline 读写使用的显式租户范围 SQL。 */
    private final PipelineMapper mapper;

    /* 远端调用前临时解密 GitLab Token 的唯一服务。 */
    private final SecretService secrets;

    public MybatisPipelineStore(PipelineMapper mapper, SecretService secrets) {
        this.mapper = mapper;
        this.secrets = secrets;
    }

    @Override
    public PipelineContext loadContext(long workspaceId, long projectId) {
        Map<String, Object> row = mapper.findContext(workspaceId, projectId).stream()
                .findFirst().orElseThrow(ResourceNotFoundException::new);
        String token = secrets.decrypt(workspaceId, text(row, "type"), new EncryptedSecret(
                text(row, "ciphertext"), text(row, "iv"), ((Number) row.get("key_version")).intValue(),
                text(row, "fingerprint")));
        return new PipelineContext(workspaceId, projectId, number(row, "repository_id"),
                number(row, "connection_id"), text(row, "base_url"), text(row, "remote_project_id"), token);
    }

    @Override
    @Transactional
    public PipelineRun save(PipelineRun pipeline) {
        mapper.upsert(pipeline.workspaceId(), pipeline.repositoryId(), pipeline.mergeRequestId(),
                pipeline.remotePipelineId(), pipeline.ref(), pipeline.commitSha(), pipeline.status(),
                pipeline.webUrl(), pipeline.startedAt(), pipeline.finishedAt(), pipeline.remoteUpdatedAt(),
                pipeline.summaryJson());
        return mapper.findByRemoteId(pipeline.workspaceId(), pipeline.repositoryId(), pipeline.remotePipelineId())
                .stream().findFirst().map(this::pipeline).orElseThrow();
    }

    @Override
    public Optional<PipelineRun> find(long workspaceId, long projectId, long pipelineId) {
        return mapper.find(workspaceId, projectId, pipelineId).stream().findFirst().map(this::pipeline);
    }

    @Override
    public List<PipelineRun> list(long workspaceId, long projectId, int limit) {
        return mapper.list(workspaceId, projectId, limit).stream().map(this::pipeline).toList();
    }

    private PipelineRun pipeline(Map<String, Object> row) {
        return new PipelineRun(number(row, "id"), number(row, "workspace_id"), number(row, "repository_id"),
                nullableNumber(row, "merge_request_id"), number(row, "remote_pipeline_id"), text(row, "ref"),
                text(row, "commit_sha"), text(row, "status"), text(row, "web_url"),
                (LocalDateTime) row.get("started_at"), (LocalDateTime) row.get("finished_at"),
                (LocalDateTime) row.get("remote_updated_at"),
                (LocalDateTime) row.get("last_synced_at"), text(row, "summary_json"));
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static Long nullableNumber(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : number(row, key);
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }
}
