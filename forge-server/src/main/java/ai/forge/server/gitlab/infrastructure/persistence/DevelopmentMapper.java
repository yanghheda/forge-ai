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
            + "FROM work_items w JOIN work_items parent ON parent.id=w.parent_id AND parent.organization_id=w.organization_id "
            + "JOIN git_repositories r ON r.organization_id=w.organization_id AND r.status='ACTIVE' "
            + "JOIN gitlab_connections c ON c.id=r.connection_id AND c.organization_id=w.organization_id AND c.status='ACTIVE' "
            + "JOIN secrets s ON s.id=c.credential_secret_id AND s.organization_id=w.organization_id "
            + "WHERE w.id=#{workItemId} AND w.organization_id=#{organizationId} "
            + "AND w.type='DEV_TASK' AND w.status IN ('TODO','IN_PROGRESS') AND w.deleted_at IS NULL "
            + "AND parent.type='REQUIREMENT' AND parent.status IN ('READY_FOR_DEV','IN_DEVELOPMENT')")
    List<Map<String, Object>> findContext(
            @Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId);

    @Select("SELECT commit_sha FROM branches WHERE organization_id=#{organizationId} AND repository_id=#{repositoryId} "
            + "AND name=#{branchName}")
    List<String> findCachedSha(
            @Param("organizationId") long organizationId,
            @Param("repositoryId") long repositoryId,
            @Param("branchName") String branchName);

    @Insert("INSERT INTO source_control_operations "
            + "(organization_id,work_item_id,operation_type,idempotency_key,request_hash,target_branch,status,created_at,updated_at) "
            + "VALUES (#{organizationId},#{workItemId},'START_DEVELOPMENT',#{idempotencyKey},#{requestHash},#{targetBranch},"
            + "'PROCESSING',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    int insertOperation(
            @Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("targetBranch") String targetBranch);

    @Select("SELECT organization_id,work_item_id,target_branch,idempotency_key,attempt_count "
            + "FROM source_control_operations WHERE status='PROCESSING' "
            + "AND updated_at < UTC_TIMESTAMP(6) - INTERVAL 30 SECOND "
            + "AND (next_attempt_at IS NULL OR next_attempt_at <= UTC_TIMESTAMP(6)) "
            + "ORDER BY updated_at,id LIMIT 1 FOR UPDATE SKIP LOCKED")
    List<Map<String, Object>> findPendingOperation();

    @Update("UPDATE source_control_operations SET next_attempt_at=UTC_TIMESTAMP(6) + INTERVAL 30 SECOND "
            + "WHERE organization_id = #{organizationId} AND work_item_id=#{workItemId} "
            + "AND idempotency_key=#{idempotencyKey} AND status='PROCESSING'")
    int leaseOperation(
            @Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId,
            @Param("idempotencyKey") String idempotencyKey);

    @Update("UPDATE source_control_operations SET attempt_count=attempt_count+1,last_error_code=#{errorCode},"
            + "next_attempt_at=UTC_TIMESTAMP(6) + INTERVAL LEAST(300,POW(2,LEAST(attempt_count,8))) SECOND,"
            + "updated_at=UTC_TIMESTAMP(6) WHERE organization_id = #{organizationId} "
            + "AND work_item_id=#{workItemId} AND idempotency_key=#{idempotencyKey} AND status='PROCESSING'")
    int deferOperation(
            @Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("errorCode") String errorCode);

    @Update("UPDATE source_control_operations SET status='MANUAL_ACTION_REQUIRED',last_error_code=#{errorCode},"
            + "next_attempt_at=NULL,updated_at=UTC_TIMESTAMP(6) WHERE organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId} AND work_item_id=#{workItemId} "
            + "AND idempotency_key=#{idempotencyKey} AND status='PROCESSING'")
    int requireManualRecovery(
            @Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("errorCode") String errorCode);

    @Select("SELECT request_hash,status FROM source_control_operations WHERE organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId} AND idempotency_key=#{idempotencyKey}")
    List<Map<String, Object>> findOperation(
            @Param("organizationId") long organizationId,
            @Param("idempotencyKey") String idempotencyKey);

    @Insert("INSERT INTO branches (organization_id,repository_id,work_item_id,name,commit_sha,status,remote_updated_at,last_synced_at) "
            + "VALUES (#{organizationId},#{repositoryId},#{workItemId},#{name},#{sha},'ACTIVE',#{remoteUpdatedAt},UTC_TIMESTAMP(6)) "
            + "ON DUPLICATE KEY UPDATE work_item_id=VALUES(work_item_id),commit_sha=VALUES(commit_sha),"
            + "status='ACTIVE',remote_updated_at=VALUES(remote_updated_at),last_synced_at=UTC_TIMESTAMP(6)")
    int upsertBranch(
            @Param("organizationId") long organizationId,
            @Param("repositoryId") long repositoryId,
            @Param("workItemId") long workItemId,
            @Param("name") String name,
            @Param("sha") String sha,
            @Param("remoteUpdatedAt") java.time.LocalDateTime remoteUpdatedAt);

    @Select("SELECT id,organization_id,repository_id,work_item_id,name,commit_sha,status,remote_updated_at,last_synced_at "
            + "FROM branches WHERE organization_id=#{organizationId} AND repository_id=#{repositoryId} AND name=#{name}")
    List<Map<String, Object>> findBranch(
            @Param("organizationId") long organizationId,
            @Param("repositoryId") long repositoryId,
            @Param("name") String name);

    @Insert("INSERT INTO merge_requests (organization_id,repository_id,work_item_id,remote_mr_iid,title,source_branch,"
            + "target_branch,state,web_url,author_external_id,head_sha,merge_status,remote_updated_at,last_synced_at,version) "
            + "VALUES (#{organizationId},#{repositoryId},#{workItemId},#{iid},#{title},#{source},#{target},#{state},"
            + "#{webUrl},#{authorId},#{headSha},#{mergeStatus},#{remoteUpdatedAt},UTC_TIMESTAMP(6),0) "
            + "ON DUPLICATE KEY UPDATE work_item_id=VALUES(work_item_id),title=VALUES(title),state=VALUES(state),"
            + "web_url=VALUES(web_url),head_sha=VALUES(head_sha),merge_status=VALUES(merge_status),"
            + "remote_updated_at=VALUES(remote_updated_at),last_synced_at=UTC_TIMESTAMP(6),version=version+1")
    int upsertMergeRequest(
            @Param("organizationId") long organizationId,
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

    @Select("SELECT id,organization_id,repository_id,work_item_id,remote_mr_iid,title,source_branch,target_branch,state,"
            + "web_url,author_external_id,head_sha,merge_status,remote_updated_at,last_synced_at,version "
            + "FROM merge_requests WHERE organization_id=#{organizationId} AND repository_id=#{repositoryId} "
            + "AND remote_mr_iid=#{iid}")
    List<Map<String, Object>> findMergeRequest(
            @Param("organizationId") long organizationId,
            @Param("repositoryId") long repositoryId,
            @Param("iid") long iid);

    @Update("UPDATE work_items SET version=version+IF(status='TODO',1,0),status='IN_PROGRESS',"
            + "updated_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{taskId} AND organization_id=#{organizationId} AND status IN ('TODO','IN_PROGRESS')")
    int startTask(@Param("organizationId") long organizationId, @Param("taskId") long taskId);

    @Update("UPDATE work_items SET version=version+IF(status='READY_FOR_DEV',1,0),status='IN_DEVELOPMENT',"
            + "updated_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{requirementId} AND organization_id=#{organizationId} AND status IN ('READY_FOR_DEV','IN_DEVELOPMENT')")
    int startRequirement(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Update("UPDATE source_control_operations SET status='COMPLETED',branch_id=#{branchId},"
            + "merge_request_id=#{mergeRequestId},updated_at=UTC_TIMESTAMP(6) WHERE organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId} AND work_item_id=#{workItemId} AND idempotency_key=#{idempotencyKey} "
            + "AND status IN ('PROCESSING','COMPLETED')")
    int completeOperation(
            @Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("branchId") long branchId,
            @Param("mergeRequestId") long mergeRequestId);

    @Update("UPDATE work_items t JOIN work_items r ON r.id=t.parent_id "
            + "AND r.organization_id=t.organization_id "
            + "SET t.status='DONE',t.version=t.version+1,t.updated_at=UTC_TIMESTAMP(6) "
            + "WHERE t.id=#{taskId} AND t.organization_id=#{organizationId} "
            + "AND t.type='DEV_TASK' AND t.status='IN_PROGRESS' AND t.version=#{expectedVersion} "
            + "AND r.type='REQUIREMENT' AND r.status='IN_DEVELOPMENT'")
    int completeTask(
            @Param("organizationId") long organizationId,
            @Param("taskId") long taskId,
            @Param("expectedVersion") long expectedVersion);
}
