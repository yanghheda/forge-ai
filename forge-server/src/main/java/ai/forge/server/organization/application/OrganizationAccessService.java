package ai.forge.server.organization.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.organization.domain.OrganizationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class OrganizationAccessService {
    /* 读取公司成员与角色事实的持久化端口。 */
    private final OrganizationStore store;

    public OrganizationAccessService(OrganizationStore store) {
        this.store = store;
    }

    public OrganizationContext requireContext(long userId) {
        return store.findContextForUser(userId).orElseThrow(ResourceNotFoundException::new);
    }
}
