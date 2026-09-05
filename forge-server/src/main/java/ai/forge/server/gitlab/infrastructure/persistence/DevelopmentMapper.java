package ai.forge.server.gitlab.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DevelopmentMapper {

    @Select("SELECT w.parent_id,r.id repository_id,r.connection_id,r.remote_project_id,r.default_branch,"
            + "c.base_url,s.type,s.ciphertext,s.iv,s.key_version,s.fingerprint "
            + "FROM work_items w JOIN work_items parent ON parent.id=w.parent_id AND parent.workspace_id=w.workspace_id "
            + "JOIN git_repositories r ON r.project_id=w.project_id AND r.workspace_id=w.workspace_id AND r.status='ACTIVE' "
            + "JOIN gitlab_connections c ON c.id=r.connection_id AND c.workspace_id=w.workspace_id AND c.status='ACTIVE' "
            + "JOIN secrets s ON s.id=c.credential_secret_id AND s.workspace_id=w.workspace_id "
            + "WHERE w.id=#{workItemId} AND w.workspace_id=#{workspaceId} AND w.project_id=#{projectId} "
            + "AND w.type='DEV_TASK' AND w.status IN ('TODO','IN_PROGRESS') AND w.deleted_at IS NULL "
            + "AND parent.type='REQUIREMENT' AND parent.status IN ('READY_FOR_DEV','IN_DEVELOPMENT')")
    List<Map<String, Object>> findContext(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Select("SELECT commit_sha FROM branches WHERE workspace_id=#{workspaceId} AND repository_id=#{repositoryId} "
            + "AND name=#{branchName}")
    List<String> findCachedSha(
            @Param("workspaceId") long workspaceId,
            @Param("repositoryId") long repositoryId,
            @Param("branchName") String branchName);

    @Insert("INSERT INTO source_control_operations "
            + "(workspace_id,project_id,work_item_id,operation_type,idempotency_key,request_hash,status,created_at,updated_at) "
            + "VALUES (#{workspaceId},#{projectId},#{workItemId},'START_DEVELOPMENT',#{idempotencyKey},#{requestHash},"
            + "'PROCESSING',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    int insertOperation(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash);

    @Select("SELECT request_hash,status FROM source_control_operations WHERE workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} AND idempotency_key=#{idempotencyKey}")
    List<Map<String, Object>> findOperation(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("idempotencyKey") String idempotencyKey);

    @Insert("INSERT INTO branches (workspace_id,repository_id,work_item_id,name,commit_sha,status,remote_updated_at,last_synced_at) "
            + "VALUES (#{workspaceId},#{repositoryId},#{workItemId},#{name},#{sha},'ACTIVE',#{remoteUpdatedAt},UTC_TIMESTAMP(6)) "
            + "ON DUPLICATE KEY UPDATE work_item_id=VALUES(work_item_id),commit_sha=VALUES(commit_sha),"
            + "status='ACTIVE',remote_updated_at=VALUES(remote_updated_at),last_synced_at=UTC_TIMESTAMP(6)")
    int upsertBranch(
            @Param("workspaceId") long workspaceId,
            @Param("repositoryId") long repositoryId,
            @Param("workItemId") long workItemId,
            @Param("name") String name,
            @Param("sha") String sha,
            @Param("remoteUpdatedAt") java.time.LocalDateTime remoteUpdatedAt);

    @Select("SELECT id,workspace_id,repository_id,work_item_id,name,commit_sha,status,remote_updated_at,last_synced_at "
            + "FROM branches WHERE workspace_id=#{workspaceId} AND repository_id=#{repositoryId} AND name=#{name}")
    List<Map<String, Object>> findBranch(
            @Param("workspaceId") long workspaceId,
            @Param("repositoryId") long repositoryId,
            @Param("name") String name);

    @Insert("INSERT INTO merge_requests (workspace_id,repository_id,work_item_id,remote_mr_iid,title,source_branch,"
            + "target_branch,state,web_url,author_external_id,head_sha,merge_status,remote_updated_at,last_synced_at,version) "
            + "VALUES (#{workspaceId},#{repositoryId},#{workItemId},#{iid},#{title},#{source},#{target},#{state},"
            + "#{webUrl},#{authorId},#{headSha},#{mergeStatus},#{remoteUpdatedAt},UTC_TIMESTAMP(6),0) "
            + "ON DUPLICATE KEY UPDATE work_item_id=VALUES(work_item_id),title=VALUES(title),state=VALUES(state),"
            + "web_url=VALUES(web_url),head_sha=VALUES(head_sha),merge_status=VALUES(merge_status),"
            + "remote_updated_at=VALUES(remote_updated_at),last_synced_at=UTC_TIMESTAMP(6),version=version+1")
    int upsertMergeRequest(
            @Param("workspaceId") long workspaceId,
            @Param("repositoryId") long repositoryId,
            @Param("workItemId") long workItemId,
            @Param("iid") long iid,
            @Param("title") String title,
            @Param("source") String source,
            @Param("target") String target,
            @Param("state") String state,
            @Param("webUrl") String webUrl,
            @Param("authorId") String authorId,
            @Param("headSha") String headSha,
            @Param("mergeStatus") String mergeStatus,
            @Param("remoteUpdatedAt") java.time.LocalDateTime remoteUpdatedAt);

    @Select("SELECT id,workspace_id,repository_id,work_item_id,remote_mr_iid,title,source_branch,target_branch,state,"
            + "web_url,author_external_id,head_sha,merge_status,remote_updated_at,last_synced_at,version "
            + "FROM merge_requests WHERE workspace_id=#{workspaceId} AND repository_id=#{repositoryId} "
            + "AND remote_mr_iid=#{iid}")
    List<Map<String, Object>> findMergeRequest(
            @Param("workspaceId") long workspaceId,
            @Param("repositoryId") long repositoryId,
            @Param("iid") long iid);

    @Update("UPDATE work_items SET version=version+IF(status='TODO',1,0),status='IN_PROGRESS',"
            + "updated_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{taskId} AND workspace_id=#{workspaceId} AND status IN ('TODO','IN_PROGRESS')")
    int startTask(@Param("workspaceId") long workspaceId, @Param("taskId") long taskId);

    @Update("UPDATE work_items SET version=version+IF(status='READY_FOR_DEV',1,0),status='IN_DEVELOPMENT',"
            + "updated_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{requirementId} AND workspace_id=#{workspaceId} AND status IN ('READY_FOR_DEV','IN_DEVELOPMENT')")
    int startRequirement(@Param("workspaceId") long workspaceId, @Param("requirementId") long requirementId);

    @Update("UPDATE source_control_operations SET status='COMPLETED',branch_id=#{branchId},"
            + "merge_request_id=#{mergeRequestId},updated_at=UTC_TIMESTAMP(6) WHERE workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} AND work_item_id=#{workItemId} AND idempotency_key=#{idempotencyKey} "
            + "AND status IN ('PROCESSING','COMPLETED')")
    int completeOperation(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("branchId") long branchId,
            @Param("mergeRequestId") long mergeRequestId);
}
