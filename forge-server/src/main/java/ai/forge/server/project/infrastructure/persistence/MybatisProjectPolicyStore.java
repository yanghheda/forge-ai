package ai.forge.server.project.infrastructure.persistence;

import ai.forge.server.project.application.ProjectPolicyStore;
import ai.forge.server.project.domain.ProjectPolicy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisProjectPolicyStore implements ProjectPolicyStore {

    /* 执行带 Workspace/Project scope 的策略 SQL。 */
    private final ProjectMapper mapper;

    public MybatisProjectPolicyStore(ProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ProjectPolicy> find(long workspaceId, long projectId) {
        return mapper.findPolicy(workspaceId, projectId).stream().findFirst().map(this::policy);
    }

    @Override
    public boolean update(
            long workspaceId,
            long projectId,
            boolean allowSkipUx,
            long userId,
            long expectedVersion) {
        return mapper.updatePolicy(workspaceId, projectId, allowSkipUx, userId, expectedVersion) == 1;
    }

    private ProjectPolicy policy(Map<String, Object> row) {
        return new ProjectPolicy(
                number(row, "project_id"),
                number(row, "workspace_id"),
                booleanValue(row.get("allow_skip_ux")),
                number(row, "updated_by"),
                ((LocalDateTime) row.get("updated_at")).toInstant(ZoneOffset.UTC),
                number(row, "version"));
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean booleanValue
                ? booleanValue
                : ((Number) value).intValue() != 0;
    }
}
