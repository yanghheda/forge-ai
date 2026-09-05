package ai.forge.server.workitem.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DevelopmentQaMapper {

    @Select("SELECT pp.ci_required,"
            + "EXISTS(SELECT 1 FROM git_repositories gr WHERE gr.workspace_id=r.workspace_id "
            + "AND gr.project_id=r.project_id AND gr.status='ACTIVE') repository_configured "
            + "FROM work_items r JOIN project_policies pp ON pp.workspace_id=r.workspace_id "
            + "AND pp.project_id=r.project_id WHERE r.id=#{requirementId} AND r.workspace_id=#{workspaceId} "
            + "AND r.project_id=#{projectId} AND r.type='REQUIREMENT' AND r.deleted_at IS NULL")
    List<Map<String, Object>> findHeader(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("requirementId") long requirementId);

    @Select("SELECT t.id,t.item_key,t.title,t.status,t.version,b.name branch_name,b.commit_sha branch_commit_sha,"
            + "mr.id merge_request_id,mr.web_url merge_request_url,mr.head_sha merge_request_head_sha,"
            + "p.id pipeline_id,p.commit_sha pipeline_commit_sha,p.status pipeline_status,p.last_synced_at "
            + "FROM work_items t "
            + "LEFT JOIN branches b ON b.id=(SELECT MAX(b2.id) FROM branches b2 "
            + "WHERE b2.workspace_id=t.workspace_id AND b2.work_item_id=t.id AND b2.status='ACTIVE') "
            + "LEFT JOIN merge_requests mr ON mr.id=(SELECT MAX(mr2.id) FROM merge_requests mr2 "
            + "WHERE mr2.workspace_id=t.workspace_id AND mr2.work_item_id=t.id) "
            + "LEFT JOIN pipeline_runs p ON p.id=(SELECT p2.id FROM pipeline_runs p2 "
            + "WHERE p2.workspace_id=t.workspace_id AND p2.merge_request_id=mr.id "
            + "ORDER BY p2.remote_updated_at DESC,p2.id DESC LIMIT 1) "
            + "WHERE t.workspace_id=#{workspaceId} AND t.project_id=#{projectId} AND t.parent_id=#{requirementId} "
            + "AND t.type='DEV_TASK' AND t.deleted_at IS NULL ORDER BY t.item_number")
    List<Map<String, Object>> findTasks(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("requirementId") long requirementId);
}
