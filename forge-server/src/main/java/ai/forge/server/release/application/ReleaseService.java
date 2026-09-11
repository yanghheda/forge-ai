package ai.forge.server.release.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.release.domain.PrecheckDecision;
import ai.forge.server.release.domain.PrecheckFacts;
import ai.forge.server.release.domain.PrecheckRuleRegistry;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class ReleaseService {

    /* Release 的租户范围、角色与原子权限裁决器。 */
    private final PermissionEvaluator permissions;
    /* Release 聚合与快照事实的 MyBatis 端口。 */
    private final ReleaseStore store;
    /* 六项确定性 Precheck 的固定注册表。 */
    private final PrecheckRuleRegistry rules = new PrecheckRuleRegistry();

    public ReleaseService(PermissionEvaluator permissions, ReleaseStore store) {
        this.permissions = permissions;
        this.store = store;
    }

    @Transactional
    public ReleaseView create(long userId, long organizationId, String versionName,
            String environment, List<Long> itemIds, long approvalTtlMinutes) {
        permissions.requireOrganization(userId, organizationId, "release.manage");
        if (versionName == null || versionName.isBlank() || environment == null || environment.isBlank()
                || itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("release version, environment and items are required");
        }
        return store.create(organizationId, userId, versionName.trim(), environment.trim(),
                itemIds.stream().distinct().toList(), approvalTtlMinutes);
    }

    public ReleaseView get(long userId, long organizationId, long releaseId) {
        permissions.requireOrganization(userId, organizationId, "release.read");
        return store.find(organizationId, releaseId).orElseThrow(ResourceNotFoundException::new);
    }

    public List<ReleaseView> list(long userId, long organizationId) {
        permissions.requireOrganization(userId, organizationId, "release.read");
        return store.list(organizationId);
    }

    @Transactional
    public ReleaseView updateNote(long userId, long organizationId, long releaseId,
            String note, long expectedVersion) {
        permissions.requireOrganization(userId, organizationId, "release.manage");
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("release note is required");
        }
        return store.updateNote(organizationId, releaseId, note.trim(), expectedVersion);
    }

    @Transactional
    public PrecheckSnapshot precheck(long userId, long organizationId, long releaseId) {
        permissions.requireOrganization(userId, organizationId, "release.precheck");
        PrecheckFacts facts = store.loadFacts(organizationId, releaseId);
        PrecheckDecision decision = rules.evaluate(facts);
        return store.appendPrecheck(organizationId, releaseId, userId, "USER", decision, facts);
    }
}
