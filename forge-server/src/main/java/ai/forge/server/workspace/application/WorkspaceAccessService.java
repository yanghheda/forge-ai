package ai.forge.server.workspace.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workspace.domain.Workspace;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class WorkspaceAccessService {

    /* 从 MySQL 读取当前成员关系和 Owner 范围的持久化端口。 */
    private final WorkspaceStore workspaceStore;

    public WorkspaceAccessService(WorkspaceStore workspaceStore) {
        this.workspaceStore = workspaceStore;
    }

    public Workspace requireMember(long userId, long workspaceId) {
        return workspaceStore.findActiveByIdAndUserId(workspaceId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public Workspace requireMemberBySlug(long userId, String slug) {
        return workspaceStore.findActiveBySlugAndUserId(slug, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public void requireOwner(long userId, long workspaceId) {
        requireMember(userId, workspaceId);
        if (!workspaceStore.isActiveOwner(workspaceId, userId)) {
            throw new ResourceNotFoundException();
        }
    }

    public long requireActiveUserId(String normalizedEmail) {
        return workspaceStore.findActiveUserIdByNormalizedEmail(normalizedEmail)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public long requireActiveMemberUserId(long workspaceId, String normalizedEmail) {
        return workspaceStore.findActiveMemberUserIdByNormalizedEmail(workspaceId, normalizedEmail)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public boolean isOwner(long userId, long workspaceId) {
        return workspaceStore.isActiveOwner(workspaceId, userId);
    }
}
