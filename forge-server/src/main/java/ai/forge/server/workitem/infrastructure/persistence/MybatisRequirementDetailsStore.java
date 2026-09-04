package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.workitem.application.RequirementDetailsStore;
import ai.forge.server.workitem.domain.RequirementDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisRequirementDetailsStore implements RequirementDetailsStore {
    /* 执行带范围与乐观锁的 SQL。 */ private final RequirementDetailsMapper mapper;
    /* 转换验收标准 JSON。 */ private final ObjectMapper objectMapper;

    public MybatisRequirementDetailsStore(RequirementDetailsMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Optional<RequirementDetails> find(long workspaceId, long projectId, long workItemId) {
        return mapper.find(workspaceId, projectId, workItemId).stream().findFirst().map(this::details);
    }

    public boolean save(long workspaceId, long projectId, long workItemId, String goal, String inScope, String outOfScope, List<String> criteria, String businessValue, long expectedVersion) {
        try {
            String json = objectMapper.writeValueAsString(criteria);
            boolean exists = find(workspaceId, projectId, workItemId).isPresent();
            int changed = exists ? mapper.update(workspaceId, projectId, workItemId, goal, inScope, outOfScope, json, businessValue, expectedVersion) : mapper.insert(workspaceId, projectId, workItemId, goal, inScope, outOfScope, json, businessValue, expectedVersion);
            return changed == 1;
        } catch (Exception exception) {
            throw new IllegalArgumentException("acceptance criteria cannot be serialized", exception);
        }
    }

    private RequirementDetails details(Map<String, Object> row) {
        try {
            return new RequirementDetails(((Number) row.get("work_item_id")).longValue(), ((Number) row.get("workspace_id")).longValue(), row.get("goal").toString(), row.get("in_scope").toString(), row.get("out_of_scope").toString(), objectMapper.readValue(row.get("acceptance_criteria_json").toString(), new TypeReference<>() {
            }), row.get("business_value").toString(), ((Number) row.get("version")).longValue(), ((LocalDateTime) row.get("updated_at")).toInstant(ZoneOffset.UTC));
        } catch (Exception exception) {
            throw new IllegalStateException("stored acceptance criteria are invalid", exception);
        }
    }
}
