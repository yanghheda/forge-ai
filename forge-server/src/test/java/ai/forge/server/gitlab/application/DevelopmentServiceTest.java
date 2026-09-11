package ai.forge.server.gitlab.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.authorization.application.PermissionStore;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.gitlab.domain.Branch;
import ai.forge.server.gitlab.domain.MergeRequest;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DevelopmentServiceTest {

    @Test
    void createsDevTaskOnlyBelowReadyRequirement() {
        FakeStore store = new FakeStore();
        DevelopmentService service = service(store, new FakeProvider());

        WorkItem created = service.createDevTask(1L, 7L, 11L, "Implement API", "Details", null);

        assertThat(created.type()).isEqualTo(WorkItemType.DEV_TASK);
        assertThat(created.status()).isEqualTo(WorkItemStatus.TODO);
        assertThat(store.parentId).isEqualTo(11L);

        store.requirement = item(WorkItemType.REQUIREMENT, WorkItemStatus.UX_IN_PROGRESS);
        assertThatThrownBy(() -> service.createDevTask(1L, 7L, 11L, "Too early", "", null))
                .isInstanceOf(DevelopmentStateException.class);
    }

    @Test
    void createsBranchAndMergeRequestThenLinksLocalSnapshots() {
        FakeStore store = new FakeStore();
        FakeProvider provider = new FakeProvider();
        DevelopmentService service = service(store, provider);

        DevelopmentResult result = service.start(1L, 7L, 12L, "main", "idem-1");

        assertThat(result.branch().name()).isEqualTo("feature/forge-12-implement-api");
        assertThat(result.mergeRequest().remoteMrIid()).isEqualTo(44L);
        assertThat(store.completed).isTrue();
        assertThat(provider.createBranchCalls).isEqualTo(1);
        assertThat(provider.createMergeRequestCalls).isEqualTo(1);
    }

    @Test
    void reusesExistingSameShaBranchAndOpenMergeRequest() {
        FakeStore store = new FakeStore();
        FakeProvider provider = new FakeProvider();
        provider.branch = Optional.of(branch("abc"));
        provider.mergeRequest = Optional.of(mergeRequest());

        DevelopmentResult result = service(store, provider).start(1L, 7L, 12L, "main", "idem-1");

        assertThat(result.reconciled()).isTrue();
        assertThat(provider.createBranchCalls).isZero();
        assertThat(provider.createMergeRequestCalls).isZero();
    }

    @Test
    void rejectsSameBranchNameAtDifferentBaseSha() {
        FakeProvider provider = new FakeProvider();
        provider.branch = Optional.of(branch("different"));

        assertThatThrownBy(() -> service(new FakeStore(), provider)
                        .start(1L, 7L, 12L, "main", "idem-1"))
                .isInstanceOf(RemoteResourceConflictException.class);
    }

    @Test
    void reconcilesAfterTimeoutAndDoesNotBlindlyRetryCreate() {
        FakeProvider provider = new FakeProvider();
        provider.timeoutAfterBranchCreation = true;

        DevelopmentResult result = service(new FakeStore(), provider)
                .start(1L, 7L, 12L, "main", "idem-1");

        assertThat(result.reconciled()).isTrue();
        assertThat(provider.createBranchCalls).isEqualTo(1);
    }

    @Test
    void leavesOperationProcessingWhenRateLimitedAndDeniesBeforeStoreReads() {
        FakeStore store = new FakeStore();
        FakeProvider provider = new FakeProvider();
        provider.rateLimited = true;

        assertThatThrownBy(() -> service(store, provider).start(1L, 7L, 12L, "main", "idem-1"))
                .isInstanceOf(GitLabRemoteException.class)
                .extracting(exception -> ((GitLabRemoteException) exception).code())
                .isEqualTo("GITLAB_RATE_LIMITED");
        assertThat(store.completed).isFalse();

        DevelopmentService denied = new DevelopmentService(
                permissions(false), store, provider);
        int reads = store.reads;
        assertThatThrownBy(() -> denied.start(2L, 7L, 12L, "main", "idem-2"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(store.reads).isEqualTo(reads);
    }

    @Test
    void completesAnInProgressDevTaskThroughTheScopedStore() {
        FakeStore store = new FakeStore();

        WorkItem completed = service(store, new FakeProvider())
                .completeTask(1L, 7L, 12L, 3L);

        assertThat(completed.status()).isEqualTo(WorkItemStatus.DONE);
        assertThat(store.completedTaskExpectedVersion).isEqualTo(3L);
    }

    @Test
    void reconciliationReusesFrozenOperationAndDefersRateLimit() {
        FakeStore store = new FakeStore();
        store.pending = Optional.of(new PendingDevelopmentOperation(
                7L, 12L, "release", "idem-recovery", 0));
        FakeProvider provider = new FakeProvider();
        provider.rateLimited = true;

        boolean processed = service(store, provider).reconcileNext();

        assertThat(processed).isTrue();
        assertThat(store.lastTargetBranch).isEqualTo("release");
        assertThat(store.lastIdempotencyKey).isEqualTo("idem-recovery");
        assertThat(store.deferredErrorCode).isEqualTo("GITLAB_RATE_LIMITED");
        assertThat(store.completed).isFalse();
    }

    private static DevelopmentService service(FakeStore store, FakeProvider provider) {
        return new DevelopmentService(permissions(true), store, provider);
    }

    private static PermissionEvaluator permissions(boolean allowed) {
        return new PermissionEvaluator(new PermissionStore() {
            @Override
            public Set<String> findOrganizationPermissionSet(long userId, long organizationId) {
                return allowed ? Set.of("task.create", "task.edit", "repo.read") : Set.of();
            }
        });
    }

    private static WorkItem item(WorkItemType type, WorkItemStatus status) {
        return new WorkItem(12L, 7L, 12L, "FORGE-12", type, "Implement API", "", status,
                WorkItemPriority.MEDIUM, null, 1L, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }

    private static Branch branch(String sha) {
        return new Branch(31L, 7L, 41L, 12L, "feature/forge-12-implement-api", sha, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static MergeRequest mergeRequest() {
        return new MergeRequest(51L, 7L, 41L, 12L, 44L, "Implement API", "feature/forge-12-implement-api",
                "main", "opened", "https://gitlab.example/mr/44", "8", "abc", "unchecked",
                LocalDateTime.now(), LocalDateTime.now(), 0L);
    }

    private static final class FakeStore implements DevelopmentStore {
        private WorkItem requirement = item(WorkItemType.REQUIREMENT, WorkItemStatus.READY_FOR_DEV);
        private Long parentId;
        private boolean completed;
        private int reads;
        private Long completedTaskExpectedVersion;
        private Optional<PendingDevelopmentOperation> pending = Optional.empty();
        private String lastTargetBranch;
        private String lastIdempotencyKey;
        private String deferredErrorCode;

        @Override
        public Optional<WorkItem> findWorkItem(long organizationId, long workItemId) {
            reads++;
            return Optional.of(workItemId == 11L ? requirement : item(WorkItemType.DEV_TASK, WorkItemStatus.TODO));
        }

        @Override
        public boolean hasActiveOrganizationMember(long organizationId, long userId) {
            return true;
        }

        @Override
        public WorkItem createDevTask(long organizationId, long actorId, long requirementId,
                String title, String description, Long assigneeUserId) {
            parentId = requirementId;
            return item(WorkItemType.DEV_TASK, WorkItemStatus.TODO);
        }

        @Override
        public DevelopmentContext begin(long organizationId, long devTaskId, String targetBranch,
                String idempotencyKey) {
            reads++;
            lastTargetBranch = targetBranch;
            lastIdempotencyKey = idempotencyKey;
            return new DevelopmentContext(item(WorkItemType.DEV_TASK, WorkItemStatus.TODO), 11L, 41L, 21L,
                    "https://gitlab.example", "123", "main", "abc", "token", idempotencyKey, false);
        }

        @Override
        public Optional<PendingDevelopmentOperation> findPendingOperation() {
            Optional<PendingDevelopmentOperation> result = pending;
            pending = Optional.empty();
            return result;
        }

        @Override
        public void defer(PendingDevelopmentOperation operation, String errorCode) {
            deferredErrorCode = errorCode;
        }

        @Override
        public DevelopmentResult complete(DevelopmentContext context, Branch branch, MergeRequest mergeRequest,
                boolean reconciled) {
            completed = true;
            return new DevelopmentResult(branch, mergeRequest, reconciled);
        }

        @Override
        public WorkItem completeTask(
                long organizationId,
                long taskId,
                long expectedVersion) {
            completedTaskExpectedVersion = expectedVersion;
            return item(WorkItemType.DEV_TASK, WorkItemStatus.DONE);
        }
    }

    private static final class FakeProvider implements SourceControlProvider {
        private Optional<Branch> branch = Optional.empty();
        private Optional<MergeRequest> mergeRequest = Optional.empty();
        private int createBranchCalls;
        private int createMergeRequestCalls;
        private boolean timeoutAfterBranchCreation;
        private boolean rateLimited;

        @Override
        public ConnectionTestResult test(String baseUrl, String token) { return null; }

        @Override
        public RepositoryDto getRepository(String baseUrl, String token, String remoteProjectId) { return null; }

        @Override
        public Optional<Branch> findBranch(DevelopmentContext context, String branchName) {
            return branch;
        }

        @Override
        public Branch createBranch(DevelopmentContext context, String branchName, String ref, String idempotencyKey) {
            createBranchCalls++;
            Branch created = branch("abc");
            branch = Optional.of(created);
            if (rateLimited) throw new GitLabRemoteException("GITLAB_RATE_LIMITED");
            if (timeoutAfterBranchCreation) throw new GitLabRemoteException("GITLAB_TIMEOUT");
            return created;
        }

        @Override
        public Optional<MergeRequest> findOpenMergeRequest(
                DevelopmentContext context, String sourceBranch, String targetBranch) {
            return mergeRequest;
        }

        @Override
        public MergeRequest createMergeRequest(DevelopmentContext context, String sourceBranch, String targetBranch,
                String title, String idempotencyKey) {
            createMergeRequestCalls++;
            MergeRequest created = mergeRequest();
            mergeRequest = Optional.of(created);
            return created;
        }
    }
}
