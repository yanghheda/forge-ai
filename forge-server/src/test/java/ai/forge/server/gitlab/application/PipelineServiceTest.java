package ai.forge.server.gitlab.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.authorization.application.PermissionStore;
import ai.forge.server.gitlab.domain.PipelineRun;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PipelineServiceTest {

    @Test
    void triggersPipelineOutsideStoreAndPersistsReturnedSnapshot() {
        FakeStore store = new FakeStore();
        FakeProvider provider = new FakeProvider();
        PipelineRun result = service(store, provider, 8).trigger(1L, 7L, 9L, " main ");

        assertThat(provider.ref).isEqualTo("main");
        assertThat(store.saved).isEqualTo(result);
        assertThat(result.remotePipelineId()).isEqualTo(81L);
    }

    @Test
    void returnsOnlyBoundedLogTailAndMarksTruncation() {
        FakeStore store = new FakeStore();
        FakeProvider provider = new FakeProvider();
        provider.log = "0123456789".getBytes(StandardCharsets.UTF_8);

        PipelineService.PipelineLogTail tail = service(store, provider, 6)
                .logTail(1L, 7L, 9L, 1L, 99L);

        assertThat(tail.content()).isEqualTo("456789");
        assertThat(tail.truncated()).isTrue();
        assertThat(provider.requestedBytes).isEqualTo(7);
    }

    private static PipelineService service(FakeStore store, FakeProvider provider, int maxBytes) {
        PermissionStore permissionStore = new PermissionStore() {
            @Override
            public Set<String> findWorkspacePermissionSet(long userId, long workspaceId) {
                return Set.of();
            }

            @Override
            public Optional<ProjectAccess> findProjectAccess(long userId, long workspaceId, long projectId) {
                return Optional.of(new ProjectAccess(true, Set.of("DEVELOPER"), Set.of("repo.read", "repo.write")));
            }

            @Override
            public List<Long> findProjectIdsWithPermission(long userId, long workspaceId, String permission) {
                return List.of(9L);
            }
        };
        return new PipelineService(new PermissionEvaluator(permissionStore), store, provider, maxBytes);
    }

    private static PipelineRun pipeline(long id) {
        LocalDateTime now = LocalDateTime.now();
        return new PipelineRun(id, 7L, 41L, 51L, 81L, "main", "abc", "running",
                "https://gitlab.example/pipelines/81", now, null, now, now, "{}");
    }

    private static final class FakeStore implements PipelineStore {
        private PipelineRun saved;

        @Override
        public PipelineContext loadContext(long workspaceId, long projectId) {
            return new PipelineContext(workspaceId, projectId, 41L, 21L,
                    "https://gitlab.example", "123", "token");
        }

        @Override
        public PipelineRun save(PipelineRun pipeline) {
            saved = new PipelineRun(1L, pipeline.workspaceId(), pipeline.repositoryId(), pipeline.mergeRequestId(),
                    pipeline.remotePipelineId(), pipeline.ref(), pipeline.commitSha(), pipeline.status(),
                    pipeline.webUrl(), pipeline.startedAt(), pipeline.finishedAt(), pipeline.remoteUpdatedAt(),
                    pipeline.lastSyncedAt(),
                    pipeline.summaryJson());
            return saved;
        }

        @Override
        public Optional<PipelineRun> find(long workspaceId, long projectId, long pipelineId) {
            return Optional.of(pipeline(pipelineId));
        }

        @Override
        public List<PipelineRun> list(long workspaceId, long projectId, int limit) {
            return new ArrayList<>();
        }
    }

    private static final class FakeProvider implements SourceControlProvider {
        private String ref;
        private byte[] log = new byte[0];
        private int requestedBytes;

        @Override
        public ConnectionTestResult test(String baseUrl, String token) {
            return null;
        }

        @Override
        public RepositoryDto getRepository(String baseUrl, String token, String remoteProjectId) {
            return null;
        }

        @Override
        public PipelineRun triggerPipeline(PipelineContext context, String ref) {
            this.ref = ref;
            return pipeline(0L);
        }

        @Override
        public byte[] getJobLog(PipelineContext context, long pipelineId, long jobId, int maxBytes) {
            requestedBytes = maxBytes;
            return log;
        }
    }
}
