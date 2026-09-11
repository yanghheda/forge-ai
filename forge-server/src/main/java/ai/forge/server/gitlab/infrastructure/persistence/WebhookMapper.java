package ai.forge.server.gitlab.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WebhookMapper {

    @Select("SELECT c.id connection_id,c.organization_id,s.type,s.ciphertext,s.iv,s.key_version,s.fingerprint "
            + "FROM gitlab_connections c JOIN secrets s ON s.id=c.webhook_secret_id AND s.organization_id=c.organization_id "
            + "WHERE c.id=#{connectionId} AND c.status='ACTIVE'")
    List<Map<String, Object>> findWebhookSecret(@Param("connectionId") long connectionId);

    @Insert("INSERT IGNORE INTO webhook_deliveries "
            + "(organization_id,connection_id,delivery_key,event_type,payload_hash,payload,status,attempts,next_attempt_at,received_at) "
            + "SELECT organization_id,id,#{deliveryKey},#{eventType},#{payloadHash},CAST(#{payload} AS JSON),'PENDING',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) "
            + "FROM gitlab_connections WHERE id=#{connectionId} AND status='ACTIVE'")
    int insertDelivery(@Param("connectionId") long connectionId, @Param("deliveryKey") String deliveryKey,
            @Param("eventType") String eventType, @Param("payloadHash") String payloadHash,
            @Param("payload") String payload);

    @Select("SELECT id,organization_id,connection_id,delivery_key,event_type,payload_hash,payload,attempts,received_at "
            + "FROM webhook_deliveries WHERE "
            + "((status IN ('PENDING','FAILED') AND next_attempt_at<=UTC_TIMESTAMP(6)) "
            + "OR (status='PROCESSING' AND next_attempt_at<UTC_TIMESTAMP(6))) "
            + "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")
    List<Map<String, Object>> claim();

    @Update("UPDATE webhook_deliveries SET status='PROCESSING',next_attempt_at=TIMESTAMPADD(SECOND,#{leaseSeconds},UTC_TIMESTAMP(6)) "
            + "WHERE id=#{id}")
    int markProcessing(@Param("id") long id, @Param("leaseSeconds") long leaseSeconds);

    @Select("SELECT r.id,r.organization_id FROM git_repositories r WHERE r.connection_id=#{connectionId} "
            + "AND r.remote_project_id=#{remoteProjectId} AND r.status='ACTIVE'")
    List<Map<String, Object>> findRepository(@Param("connectionId") long connectionId,
            @Param("remoteProjectId") String remoteProjectId);

    @Select("SELECT remote_updated_at FROM merge_requests WHERE organization_id=#{organizationId} "
            + "AND repository_id=#{repositoryId} AND remote_mr_iid=#{remoteId} FOR UPDATE")
    List<java.time.LocalDateTime> lockMergeRequestTime(@Param("organizationId") long organizationId,
            @Param("repositoryId") long repositoryId, @Param("remoteId") long remoteId);

    @Select("SELECT remote_updated_at FROM pipeline_runs WHERE organization_id=#{organizationId} "
            + "AND repository_id=#{repositoryId} AND remote_pipeline_id=#{remoteId} FOR UPDATE")
    List<java.time.LocalDateTime> lockPipelineTime(@Param("organizationId") long organizationId,
            @Param("repositoryId") long repositoryId, @Param("remoteId") long remoteId);

    @Insert("INSERT INTO merge_requests (organization_id,repository_id,work_item_id,remote_mr_iid,title,source_branch,"
            + "target_branch,state,web_url,author_external_id,head_sha,merge_status,remote_updated_at,last_synced_at,version) "
            + "VALUES (#{organizationId},#{repositoryId},NULL,#{iid},#{title},#{source},#{target},#{state},#{webUrl},"
            + "#{authorId},#{headSha},#{mergeStatus},#{updatedAt},UTC_TIMESTAMP(6),0) ON DUPLICATE KEY UPDATE "
            + "title=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(title),title),"
            + "state=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(state),state),"
            + "web_url=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(web_url),web_url),"
            + "head_sha=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(head_sha),head_sha),"
            + "merge_status=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(merge_status),merge_status),"
            + "last_synced_at=IF(remote_updated_at<VALUES(remote_updated_at),UTC_TIMESTAMP(6),last_synced_at),"
            + "version=IF(remote_updated_at<VALUES(remote_updated_at),version+1,version),"
            + "remote_updated_at=GREATEST(remote_updated_at,VALUES(remote_updated_at))")
    int upsertMergeRequest(@Param("organizationId") long organizationId, @Param("repositoryId") long repositoryId,
            @Param("iid") long iid, @Param("title") String title, @Param("source") String source,
            @Param("target") String target, @Param("state") String state, @Param("webUrl") String webUrl,
            @Param("authorId") String authorId, @Param("headSha") String headSha,
            @Param("mergeStatus") String mergeStatus, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Insert("INSERT INTO pipeline_runs (organization_id,repository_id,merge_request_id,remote_pipeline_id,ref,commit_sha,"
            + "status,web_url,started_at,finished_at,remote_updated_at,last_synced_at,summary_json) "
            + "VALUES (#{organizationId},#{repositoryId},NULL,#{pipelineId},#{ref},#{sha},#{status},#{webUrl},#{startedAt},"
            + "#{finishedAt},#{updatedAt},UTC_TIMESTAMP(6),JSON_OBJECT()) ON DUPLICATE KEY UPDATE "
            + "status=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(status),status),"
            + "web_url=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(web_url),web_url),"
            + "started_at=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(started_at),started_at),"
            + "finished_at=IF(remote_updated_at<VALUES(remote_updated_at),VALUES(finished_at),finished_at),"
            + "last_synced_at=IF(remote_updated_at<VALUES(remote_updated_at),UTC_TIMESTAMP(6),last_synced_at),"
            + "remote_updated_at=GREATEST(remote_updated_at,VALUES(remote_updated_at))")
    int upsertPipeline(@Param("organizationId") long organizationId, @Param("repositoryId") long repositoryId,
            @Param("pipelineId") long pipelineId, @Param("ref") String ref, @Param("sha") String sha,
            @Param("status") String status, @Param("webUrl") String webUrl,
            @Param("startedAt") java.time.LocalDateTime startedAt,
            @Param("finishedAt") java.time.LocalDateTime finishedAt,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Insert("INSERT INTO outbox_events (aggregate_type,aggregate_id,event_type,payload,created_at,processed_at) "
            + "VALUES (#{aggregateType},#{aggregateId},#{eventType},CAST(#{payload} AS JSON),UTC_TIMESTAMP(6),NULL)")
    int insertOutbox(@Param("aggregateType") String aggregateType, @Param("aggregateId") long aggregateId,
            @Param("eventType") String eventType, @Param("payload") String payload);

    @Update("UPDATE webhook_deliveries SET status=#{status},payload=NULL,error_message=NULL,processed_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{id} AND status='PROCESSING'")
    int markDone(@Param("id") long id, @Param("status") String status);

    @Update("UPDATE webhook_deliveries SET attempts=attempts+1,status=IF(attempts>=#{maxAttempts},'DEAD','FAILED'),"
            + "next_attempt_at=TIMESTAMPADD(MICROSECOND,#{backoffMicros},UTC_TIMESTAMP(6)),error_message=#{error} "
            + "WHERE id=#{id} AND status='PROCESSING'")
    int markFailed(@Param("id") long id, @Param("maxAttempts") int maxAttempts,
            @Param("backoffMicros") long backoffMicros, @Param("error") String error);
}
