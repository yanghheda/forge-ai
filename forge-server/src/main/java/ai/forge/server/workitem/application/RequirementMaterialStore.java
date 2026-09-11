package ai.forge.server.workitem.application;

import java.util.Optional;

public interface RequirementMaterialStore {

    Optional<RequirementMaterial> find(long organizationId, long workItemId);

    boolean hasPublishedPrd(long organizationId, long workItemId);

    boolean hasPublishedUxSpec(long organizationId, long workItemId);

    boolean allowsSkipUx(long organizationId);

    boolean hasEligibleSkipUxLabel(long organizationId, long workItemId);

    record RequirementMaterial(
            /* 非空业务目标。 */
            String goal,
            /* 非空需求纳入范围。 */
            String inScope,
            /* JSON 数组中的验收标准数量。 */
            int acceptanceCriteriaCount) {}
}
