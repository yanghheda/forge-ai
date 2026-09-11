package ai.forge.server.authorization.application;

import java.util.Set;

public interface PermissionStore {

    Set<String> findOrganizationPermissionSet(long userId, long organizationId);
}
