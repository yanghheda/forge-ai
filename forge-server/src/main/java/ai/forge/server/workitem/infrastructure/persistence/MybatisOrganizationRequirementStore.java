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
            long organizationId,
            Long participantUserId,
            String query,
            WorkItemStatus status,
            int page,
            int pageSize) {
        String statusValue = status == null ? null : status.name();
        List<OrganizationRequirementView> items = mapper.findRequirements(
                        organizationId, participantUserId, query, statusValue, pageSize, (page - 1) * pageSize)
                .stream()
                .map(this::requirement)
                .toList();
        long total = mapper.countRequirements(organizationId, participantUserId, query, statusValue);
        return new OrganizationRequirementPage(items, page, pageSize, total);
    }

    @Override
    public RequirementOverview overview(long organizationId) {
        Map<String, Object> row = mapper.overview(organizationId);
        return new RequirementOverview(number(row, "total"), number(row, "in_progress"), number(row, "completed"));
    }

    @Override
    public List<RequirementParticipantView> findParticipants(
            long organizationId, long requirementId) {
        return mapper.findParticipants(organizationId, requirementId).stream()
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
            long organizationId,
            long requirementId,
            long actorUserId,
            List<ParticipantAssignment> assignments) {
        mapper.deleteParticipants(organizationId, requirementId);
        for (ParticipantAssignment assignment : assignments) {
            mapper.insertParticipant(
                    organizationId,
                    requirementId,
                    assignment.role().name(),
                    assignment.userId(),
                    actorUserId);
        }
        return findParticipants(organizationId, requirementId);
    }

    @Override
    public boolean memberCanFillRole(
            long organizationId, long userId, RequirementParticipantRole role) {
        return mapper.memberCanFillRole(organizationId, userId, role.name());
    }

    @Override
    public List<RequirementMemberView> findMembers(long organizationId) {
        return mapper.findMembers(organizationId).stream()
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
