package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.ActivityItem;
import ai.forge.server.workitem.domain.WorkItemLabel;
import ai.forge.server.workitem.domain.WorkItemRelation;
import ai.forge.server.workitem.domain.WorkItemRelationType;
import java.util.List;

public interface WorkItemCollaborationStore {

    boolean relationExists(
            long organizationId,
            long sourceId,
            long targetId,
            WorkItemRelationType relationType);

    WorkItemRelation createRelation(
            long organizationId,
            long sourceId,
            long targetId,
            WorkItemRelationType relationType,
            long createdBy);

    List<WorkItemRelation> findRelations(long organizationId, long workItemId);

    void addLabel(long organizationId, long workItemId, WorkItemLabel label, long createdBy);

    ActivityItem createComment(
            long organizationId, long workItemId, long authorId, String body);

    List<ActivityItem> findActivity(long organizationId, long workItemId);
}
