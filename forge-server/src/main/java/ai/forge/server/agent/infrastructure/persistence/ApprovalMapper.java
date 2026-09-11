package ai.forge.server.agent.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalMapper {

    @Insert("INSERT INTO approvals (id, organization_id, run_id, tool_call_id, type, risk_level, status, "
            + "requested_by, tool_name, tool_version, argument_hash, frozen_arguments_encrypted, "
            + "resource_versions_json, reason, expires_at, version, created_at) VALUES "
            + "(#{id}, #{organizationId}, #{runId}, #{toolCallId}, 'TOOL_EXECUTION', #{riskLevel}, "
            + "'PENDING', #{requestedBy}, #{toolName}, #{toolVersion}, #{argumentHash}, #{encryptedArguments}, "
            + "CAST(#{resourceVersionsJson} AS JSON), #{reason}, #{expiresAt}, 0, UTC_TIMESTAMP(6))")
    int insert(@Param("id") String id, @Param("organizationId") long organizationId, @Param("runId") String runId,
            @Param("toolCallId") String toolCallId, @Param("riskLevel") String riskLevel,
            @Param("requestedBy") long requestedBy, @Param("toolName") String toolName,
            @Param("toolVersion") int toolVersion, @Param("argumentHash") String argumentHash,
            @Param("encryptedArguments") String encryptedArguments,
            @Param("resourceVersionsJson") String resourceVersionsJson, @Param("reason") String reason,
            @Param("expiresAt") java.time.LocalDateTime expiresAt);

    @Select("SELECT * FROM approvals WHERE organization_id = #{organizationId} "
            + "AND id = #{approvalId}")
    List<Map<String, Object>> find(@Param("organizationId") long organizationId, @Param("approvalId") String approvalId);

    @Select("SELECT * FROM approvals WHERE organization_id = #{organizationId} "
            + "AND run_id = #{runId} AND tool_call_id = #{toolCallId}")
    List<Map<String, Object>> findByCall(@Param("organizationId") long organizationId, @Param("runId") String runId,
            @Param("toolCallId") String toolCallId);

    @Select("SELECT * FROM approvals WHERE organization_id = #{organizationId} "
            + "AND run_id = #{runId} ORDER BY created_at DESC LIMIT 1")
    List<Map<String, Object>> findByRun(@Param("organizationId") long organizationId, @Param("runId") String runId);

    @Update("UPDATE approvals SET status = #{status}, approver_user_id = #{approverId}, "
            + "decided_at = UTC_TIMESTAMP(6), version = version + 1 WHERE organization_id = #{organizationId} "
            + "AND organization_id = #{organizationId} AND id = #{approvalId} AND status = 'PENDING' "
            + "AND version = #{expectedVersion} AND expires_at > UTC_TIMESTAMP(6)")
    int decide(@Param("organizationId") long organizationId,
            @Param("approvalId") String approvalId, @Param("approverId") long approverId,
            @Param("status") String status, @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE approvals SET status = 'EXPIRED', decided_at = UTC_TIMESTAMP(6), version = version + 1 "
            + "WHERE organization_id = #{organizationId} AND id = #{approvalId} "
            + "AND status = 'PENDING' AND expires_at <= UTC_TIMESTAMP(6)")
    int expire(@Param("organizationId") long organizationId,
            @Param("approvalId") String approvalId);

    @Insert("INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at, processed_at) "
            + "VALUES ('AGENT_RUN', 0, 'AGENT_RUN_RESUME_REQUESTED', JSON_OBJECT('organizationId', #{organizationId}, "
            + "'organizationId', #{organizationId}, 'runId', #{runId}, 'approvalId', #{approvalId}), UTC_TIMESTAMP(6), NULL)")
    int insertResumeOutbox(@Param("organizationId") long organizationId,
            @Param("runId") String runId, @Param("approvalId") String approvalId);

    @Insert("INSERT INTO agent_tool_calls (run_id, organization_id, tool_call_id, tool_name, tool_version, "
            + "risk_level, argument_hash, status, arguments_json, result_json, idempotency_key, approval_id, "
            + "created_at, finished_at) VALUES (#{runId}, #{organizationId}, #{toolCallId}, #{toolName}, "
            + "#{toolVersion}, #{riskLevel}, #{argumentHash}, 'WAITING_APPROVAL', CAST(#{argumentsJson} AS JSON), "
            + "NULL, #{idempotencyKey}, #{approvalId}, UTC_TIMESTAMP(6), NULL)")
    int insertWaitingToolCall(@Param("organizationId") long organizationId,
            @Param("runId") String runId, @Param("toolCallId") String toolCallId,
            @Param("toolName") String toolName, @Param("toolVersion") int toolVersion,
            @Param("riskLevel") String riskLevel, @Param("argumentHash") String argumentHash,
            @Param("argumentsJson") String argumentsJson, @Param("idempotencyKey") String idempotencyKey,
            @Param("approvalId") String approvalId);

    @Update("UPDATE agent_runs SET status = 'WAITING_APPROVAL', last_sequence = last_sequence + 1, "
            + "version = version + 1, updated_at = UTC_TIMESTAMP(6) WHERE id = #{runId} "
            + "AND organization_id = #{organizationId} AND status = 'RUNNING'")
    int markRunWaiting(@Param("organizationId") long organizationId,
            @Param("runId") String runId);

    @Insert("INSERT INTO agent_events (run_id, sequence, event_type, request_id, payload_json, created_at) "
            + "SELECT id, last_sequence, 'approval.required', #{requestId}, "
            + "JSON_OBJECT('approvalId', #{approvalId}, 'toolName', #{toolName}, 'status', 'PENDING'), "
            + "UTC_TIMESTAMP(6) FROM agent_runs WHERE id = #{runId} AND organization_id = #{organizationId} "
            + "AND organization_id = #{organizationId}")
    int insertRequiredEvent(@Param("organizationId") long organizationId,
            @Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("toolName") String toolName, @Param("requestId") String requestId);

    @Update("UPDATE agent_runs SET status = 'RUNNING', last_sequence = last_sequence + 1, version = version + 1, "
            + "updated_at = UTC_TIMESTAMP(6) WHERE id = #{runId} AND organization_id = #{organizationId} "
            + "AND organization_id = #{organizationId} AND status = 'WAITING_APPROVAL'")
    int markRunResuming(@Param("organizationId") long organizationId,
            @Param("runId") String runId);

    @Insert("INSERT INTO agent_events (run_id, sequence, event_type, request_id, payload_json, created_at) "
            + "SELECT id, last_sequence, #{eventType}, #{requestId}, JSON_OBJECT('approvalId', #{approvalId}, "
            + "'status', #{status}) , UTC_TIMESTAMP(6) FROM agent_runs WHERE id = #{runId} "
            + "AND organization_id = #{organizationId}")
    int insertDecisionEvent(@Param("organizationId") long organizationId,
            @Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("eventType") String eventType, @Param("status") String status,
            @Param("requestId") String requestId);

    @Update("UPDATE agent_tool_calls SET status = 'SUCCEEDED', result_json = CAST(#{resultJson} AS JSON), "
            + "finished_at = UTC_TIMESTAMP(6) WHERE run_id = #{runId} AND organization_id = #{organizationId} "
            + "AND organization_id = #{organizationId} AND tool_call_id = #{toolCallId} AND status = 'WAITING_APPROVAL'")
    int completeToolCall(@Param("organizationId") long organizationId,
            @Param("runId") String runId, @Param("toolCallId") String toolCallId,
            @Param("resultJson") String resultJson);
}
