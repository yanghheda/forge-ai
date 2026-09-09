package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.util.List;

public interface OrganizationRequirementStore {

    OrganizationRequirementPage findRequirements(
            long workspaceId,
            long projectId,
            Long participantUserId,
            String query,
            WorkItemStatus status,
            int page,
            int pageSize);

    RequirementOverview overview(long workspaceId, long projectId);

    List<RequirementParticipantView> findParticipants(long workspaceId, long projectId, long requirementId);

    List<RequirementParticipantView> replaceParticipants(
            long workspaceId,
            long projectId,
            long requirementId,
            long actorUserId,
            List<ParticipantAssignment> assignments);

    boolean memberCanFillRole(
            long workspaceId, long projectId, long userId, RequirementParticipantRole role);

    List<RequirementMemberView> findMembers(long workspaceId, long projectId);

    record ParticipantAssignment(
            /* 被设置的固定需求角色。 */ RequirementParticipantRole role,
            /* 承担该需求角色的组织成员标识。 */ long userId) {}
}
