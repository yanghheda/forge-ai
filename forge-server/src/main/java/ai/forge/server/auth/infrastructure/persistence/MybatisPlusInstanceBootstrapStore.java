package ai.forge.server.auth.infrastructure.persistence;

import ai.forge.server.auth.application.InstanceBootstrapStore;
import ai.forge.server.auth.domain.BootstrapCommand;
import ai.forge.server.auth.domain.BootstrapResult;
import ai.forge.server.auth.domain.InstanceAlreadyInitializedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisPlusInstanceBootstrapStore implements InstanceBootstrapStore {

    /* 执行初始化 SQL 的 MyBatis-Plus Mapper，所有调用共享当前数据库事务。 */
    private final InstanceBootstrapMapper bootstrapMapper;

    public MybatisPlusInstanceBootstrapStore(InstanceBootstrapMapper bootstrapMapper) {
        this.bootstrapMapper = bootstrapMapper;
    }

    @Override
    public boolean isInitialized() {
        return bootstrapMapper.isInitialized();
    }

    @Override
    @Transactional
    public BootstrapResult create(BootstrapCommand command, String normalizedEmail, String passwordHash) {
        if (bootstrapMapper.lockAndGetInitializedAt() != null) {
            throw new InstanceAlreadyInitializedException();
        }
        bootstrapMapper.insertUser(
                command.adminEmail().trim(), normalizedEmail, command.adminDisplayName().trim(), passwordHash);
        long userId = bootstrapMapper.lastInsertId();
        bootstrapMapper.insertOrganization(command.organizationName().trim(), command.organizationSlug(), userId);
        long organizationId = bootstrapMapper.lastInsertId();
        bootstrapMapper.insertWorkspace(organizationId, command.workspaceName().trim(), command.workspaceSlug());
        long workspaceId = bootstrapMapper.lastInsertId();
        bootstrapMapper.insertDefaultProject(workspaceId, command.organizationName().trim(), userId);
        long projectId = bootstrapMapper.lastInsertId();
        bootstrapMapper.insertProjectSequence(projectId);
        bootstrapMapper.insertWorkspaceMember(workspaceId, userId);
        long workspaceMemberId = bootstrapMapper.lastInsertId();
        long ownerRoleId = bootstrapMapper.findOwnerRoleId();
        bootstrapMapper.insertMemberRole(workspaceMemberId, ownerRoleId);
        bootstrapMapper.insertBootstrapAudit(
                workspaceId, userId, command.requestId(), command.organizationSlug(), command.workspaceSlug());
        if (bootstrapMapper.markInitialized(organizationId, workspaceId, projectId) != 1) {
            throw new InstanceAlreadyInitializedException();
        }
        return new BootstrapResult(
                userId,
                organizationId,
                workspaceId,
                projectId,
                command.organizationSlug(),
                command.workspaceSlug());
    }
}
