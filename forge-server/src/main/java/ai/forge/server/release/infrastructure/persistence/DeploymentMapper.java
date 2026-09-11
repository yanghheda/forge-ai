package ai.forge.server.release.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DeploymentMapper {

    @Insert("INSERT INTO deployments (organization_id,release_id,mode,status,requested_by,"
            + "approval_expires_at,release_version,precheck_id,argument_hash,simulate_failure,idempotency_key,"
            + "approval_source,agent_approval_id,created_at,version) VALUES (#{organizationId},"
            + "#{releaseId},'SIMULATED',#{status},#{requestedBy},#{expiresAt},#{releaseVersion},#{precheckId},"
            + "#{argumentHash},#{simulateFailure},#{idempotencyKey},#{approvalSource},#{agentApprovalId},"
            + "UTC_TIMESTAMP(6),0)")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Map<String, Object> row, @Param("organizationId") long organizationId, @Param("releaseId") long releaseId,
            @Param("status") String status, @Param("requestedBy") long requestedBy,
            @Param("expiresAt") LocalDateTime expiresAt, @Param("releaseVersion") long releaseVersion,
            @Param("precheckId") long precheckId, @Param("argumentHash") String argumentHash,
            @Param("simulateFailure") boolean simulateFailure, @Param("idempotencyKey") String idempotencyKey,
            @Param("approvalSource") String approvalSource, @Param("agentApprovalId") String agentApprovalId);

    @Select("SELECT * FROM deployments WHERE id=#{deploymentId} AND organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId}")
    List<Map<String, Object>> find(@Param("organizationId") long organizationId, @Param("deploymentId") long deploymentId);

    @Select("SELECT * FROM deployments WHERE organization_id = #{organizationId} "
            + "AND release_id=#{releaseId} ORDER BY id DESC")
    List<Map<String, Object>> list(@Param("organizationId") long organizationId, @Param("releaseId") long releaseId);

    @Select("SELECT * FROM deployments WHERE organization_id = #{organizationId} "
            + "AND idempotency_key=#{idempotencyKey}")
    List<Map<String, Object>> findByIdempotencyKey(@Param("organizationId") long organizationId, @Param("idempotencyKey") String idempotencyKey);

    @Update("UPDATE deployments SET status=#{status},approver_user_id=#{approverUserId},"
            + "decided_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=#{deploymentId} "
            + "AND organization_id = #{organizationId} AND status='PENDING_APPROVAL' "
            + "AND approval_expires_at>UTC_TIMESTAMP(6) AND version=#{expectedVersion}")
    int decide(@Param("organizationId") long organizationId,
            @Param("deploymentId") long deploymentId, @Param("approverUserId") long approverUserId,
            @Param("status") String status, @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE deployments SET status='EXPIRED',decided_at=UTC_TIMESTAMP(6),version=version+1 "
            + "WHERE id=#{deploymentId} AND organization_id = #{organizationId} "
            + "AND status='PENDING_APPROVAL' AND approval_expires_at<=UTC_TIMESTAMP(6) AND version=#{expectedVersion}")
    int expire(@Param("organizationId") long organizationId,
            @Param("deploymentId") long deploymentId, @Param("expectedVersion") long expectedVersion);

    @Select("SELECT * FROM deployments WHERE status='APPROVED' ORDER BY id LIMIT 20")
    List<Map<String, Object>> findApproved();

    @Update("UPDATE deployments SET status='DEPLOYING',started_at=UTC_TIMESTAMP(6),version=version+1 "
            + "WHERE id=#{deploymentId} AND status='APPROVED' AND version=#{expectedVersion}")
    int claim(@Param("deploymentId") long deploymentId, @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE deployments SET status=#{status},result_code=#{resultCode},result_summary=#{resultSummary},"
            + "finished_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=#{deploymentId} AND status='DEPLOYING'")
    int complete(@Param("deploymentId") long deploymentId, @Param("status") String status,
            @Param("resultCode") String resultCode, @Param("resultSummary") String resultSummary);

    @Update("UPDATE releases SET status=#{status},updated_at=UTC_TIMESTAMP(6) "
            + "WHERE id=#{releaseId} AND organization_id = #{organizationId}")
    int updateReleaseStatus(@Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId, @Param("status") String status);

    @Insert("INSERT INTO work_item_events (organization_id,work_item_id,event_type,from_status,to_status,"
            + "actor_type,actor_id,reason,metadata_json,idempotency_key,created_at) SELECT #{organizationId},"
            + "item.id,#{eventType},#{fromStatus},#{toStatus},'SYSTEM',#{actorId},"
            + "'Successful simulated deployment',JSON_OBJECT('deploymentId',#{deploymentId},'mode','SIMULATED'),"
            + "CONCAT('deployment:',#{deploymentId},':',#{eventType}),UTC_TIMESTAMP(6) FROM work_items item "
            + "JOIN release_items ri ON ri.work_item_id=item.id WHERE ri.release_id=#{releaseId} "
            + "AND ri.organization_id=#{organizationId} AND item.organization_id=#{organizationId} "
            + "AND item.type='REQUIREMENT' AND item.status=#{fromStatus}")
    int insertRequirementEvents(@Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId, @Param("actorId") long actorId,
            @Param("deploymentId") long deploymentId, @Param("eventType") String eventType,
            @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);

    @Update("UPDATE work_items item JOIN release_items ri ON ri.work_item_id=item.id "
            + "SET item.status=#{toStatus},item.updated_at=UTC_TIMESTAMP(6),item.version=item.version+1 "
            + "WHERE ri.release_id=#{releaseId} AND ri.organization_id=#{organizationId} "
            + "AND item.organization_id=#{organizationId} "
            + "AND item.type='REQUIREMENT' AND item.status=#{fromStatus}")
    int transitionRequirements(@Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId, @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus);

    @Insert("INSERT INTO audit_logs (organization_id,actor_type,actor_id,action,resource_type,resource_id,"
            + "result,request_id,run_id,metadata_redacted_json,created_at) VALUES (#{organizationId},"
            + "#{actorType},#{actorId},#{action},'DEPLOYMENT',#{deploymentId},#{result},#{requestId},#{runId},"
            + "JSON_OBJECT('mode','SIMULATED'),UTC_TIMESTAMP(6))")
    int audit(@Param("organizationId") long organizationId,
            @Param("actorType") String actorType, @Param("actorId") long actorId,
            @Param("action") String action, @Param("deploymentId") long deploymentId,
            @Param("result") String result, @Param("requestId") String requestId,
            @Param("runId") String runId);
}
