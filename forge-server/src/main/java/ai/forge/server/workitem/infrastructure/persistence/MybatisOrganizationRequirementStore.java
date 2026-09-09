package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.workitem.application.OrganizationRequirementPage;
import ai.forge.server.workitem.application.OrganizationRequirementStore;
import ai.forge.server.workitem.application.OrganizationRequirementView;
import ai.forge.server.workitem.application.RequirementMemberView;
import ai.forge.server.workitem.application.RequirementOverview;
import ai.forge.server.workitem.application.RequirementParticipantView;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisOrganizationRequirementStore implements OrganizationRequirementStore {

    /* 执行只包含服务端解析 scope 的需求检索、统计和参与人 SQL。 */
    private final OrganizationRequirementMapper mapper;

    public MybatisOrganizationRequirementStore(OrganizationRequirementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public OrganizationRequirementPage findRequirements(
            long workspaceId,
            long projectId,
            Long participantUserId,
            String query,
            WorkItemStatus status,
            int page,
            int pageSize) {
        String statusValue = status == null ? null : status.name();
        List<OrganizationRequirementView> items = mapper.findRequirements(
                        workspaceId, projectId, participantUserId, query, statusValue, pageSize, (page - 1) * pageSize)
                .stream()
                .map(this::requirement)
                .toList();
        long total = mapper.countRequirements(workspaceId, projectId, participantUserId, query, statusValue);
        return new OrganizationRequirementPage(items, page, pageSize, total);
    }

    @Override
    public RequirementOverview overview(long workspaceId, long projectId) {
        Map<String, Object> row = mapper.overview(workspaceId, projectId);
        return new RequirementOverview(number(row, "total"), number(row, "in_progress"), number(row, "completed"));
    }

    @Override
    public List<RequirementParticipantView> findParticipants(
            long workspaceId, long projectId, long requirementId) {
        return mapper.findParticipants(workspaceId, projectId, requirementId).stream()
                .map(row -> new RequirementParticipantView(
                        RequirementParticipantRole.valueOf(text(row, "role_code")),
                        number(row, "user_id"),
                        text(row, "display_name"),
                        text(row, "email")))
                .toList();
    }

    @Override
    @Transactional
    public List<RequirementParticipantView> replaceParticipants(
            long workspaceId,
            long projectId,
            long requirementId,
            long actorUserId,
            List<ParticipantAssignment> assignments) {
        mapper.deleteParticipants(workspaceId, projectId, requirementId);
        for (ParticipantAssignment assignment : assignments) {
            mapper.insertParticipant(
                    workspaceId,
                    projectId,
                    requirementId,
                    assignment.role().name(),
                    assignment.userId(),
                    actorUserId);
        }
        return findParticipants(workspaceId, projectId, requirementId);
    }

    @Override
    public boolean memberCanFillRole(
            long workspaceId, long projectId, long userId, RequirementParticipantRole role) {
        return mapper.memberCanFillRole(workspaceId, projectId, userId, role.name());
    }

    @Override
    public List<RequirementMemberView> findMembers(long workspaceId, long projectId) {
        return mapper.findMembers(workspaceId, projectId).stream()
                .map(row -> new RequirementMemberView(
                        number(row, "user_id"),
                        text(row, "display_name"),
                        text(row, "email"),
                        Arrays.stream(text(row, "roles").split(",")).toList()))
                .toList();
    }

    private OrganizationRequirementView requirement(Map<String, Object> row) {
        return new OrganizationRequirementView(
                number(row, "id"),
                text(row, "item_key"),
                text(row, "title"),
                text(row, "description"),
                WorkItemStatus.valueOf(text(row, "status")),
                WorkItemPriority.valueOf(text(row, "priority")),
                number(row, "version"),
                instant(row, "created_at"),
                instant(row, "updated_at"));
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private Instant instant(Map<String, Object> row, String key) {
        return ((LocalDateTime) row.get(key)).toInstant(ZoneOffset.UTC);
    }
}
