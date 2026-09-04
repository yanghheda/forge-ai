package ai.forge.server.agent.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentRunMapper {

    @Insert("INSERT INTO agent_runs (id, workspace_id, project_id, work_item_id, user_id, skill, message_redacted, "
            + "client_request_id, request_hash, status, model_provider, model_name, prompt_version, started_at, finished_at, "
            + "token_input, token_output, cost, error_code, last_sequence, version, created_at, updated_at) VALUES "
            + "(#{runId}, #{workspaceId}, #{projectId}, #{workItemId}, #{userId}, #{skill}, #{messageRedacted}, "
            + "#{clientRequestId}, #{requestHash}, 'QUEUED', NULL, NULL, 'fake-v1', NULL, NULL, 0, 0, 0, NULL, 1, 0, "
            + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertRun(
            @Param("runId") String runId,
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") Long workItemId,
            @Param("userId") long userId,
            @Param("skill") String skill,
            @Param("messageRedacted") String messageRedacted,
            @Param("clientRequestId") String clientRequestId,
            @Param("requestHash") String requestHash);

    @Insert("INSERT INTO agent_events (run_id, sequence, event_type, request_id, payload_json, created_at) "
            + "SELECT r.id, #{sequence}, #{eventType}, #{requestId}, CAST(#{payloadJson} AS JSON), UTC_TIMESTAMP(6) "
            + "FROM agent_runs r WHERE r.id = #{runId} AND r.workspace_id = #{workspaceId} "
            + "AND r.project_id = #{projectId}")
    int insertEvent(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId,
            @Param("sequence") long sequence,
            @Param("eventType") String eventType,
            @Param("requestId") String requestId,
            @Param("payloadJson") String payloadJson);

    @Select("SELECT id, workspace_id, project_id, work_item_id, user_id, skill, status, last_sequence, started_at, "
            + "finished_at, error_code, created_at FROM agent_runs WHERE workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND id = #{runId}")
    List<Map<String, Object>> findRun(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId);

    @Select("SELECT id, workspace_id, project_id, work_item_id, user_id, skill, status, last_sequence, started_at, "
            + "finished_at, error_code, created_at, request_hash FROM agent_runs WHERE workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} AND user_id = #{userId} AND client_request_id = #{clientRequestId}")
    List<Map<String, Object>> findByClientRequest(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("clientRequestId") String clientRequestId);

    @Select("SELECT s.step_no, s.type, s.name, s.status, s.output_summary, s.started_at, s.finished_at FROM agent_steps s "
            + "JOIN agent_runs r ON r.id = s.run_id WHERE r.workspace_id = #{workspaceId} "
            + "AND r.project_id = #{projectId} AND r.id = #{runId} ORDER BY s.step_no")
    List<Map<String, Object>> findSteps(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId);

    @Select("SELECT e.run_id, e.sequence, e.event_type, e.request_id, e.payload_json, e.created_at "
            + "FROM agent_events e JOIN agent_runs r ON r.id = e.run_id WHERE r.workspace_id = #{workspaceId} "
            + "AND r.project_id = #{projectId} AND r.id = #{runId} AND e.sequence > #{afterSequence} "
            + "ORDER BY e.sequence LIMIT #{limit}")
    List<Map<String, Object>> findEventsAfter(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId,
            @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);

    @Select("SELECT status, last_sequence FROM agent_runs WHERE id = #{runId} AND workspace_id = #{workspaceId} "
            + "AND project_id = #{projectId} FOR UPDATE")
    List<Map<String, Object>> lockRun(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId);

    @Update("UPDATE agent_runs SET status = 'RUNNING', started_at = UTC_TIMESTAMP(6), last_sequence = #{lastSequence}, "
            + "updated_at = UTC_TIMESTAMP(6), version = version + 1 WHERE id = #{runId} "
            + "AND workspace_id = #{workspaceId} AND project_id = #{projectId} AND status = 'QUEUED'")
    int markRunning(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId,
            @Param("lastSequence") long lastSequence);

    @Insert("INSERT INTO agent_steps (run_id, step_no, type, name, status, input_summary, output_summary, "
            + "error_code, started_at, finished_at) SELECT r.id, 1, 'PLAN', 'Create plan', "
            + "'RUNNING', 'Request content redacted', NULL, NULL, UTC_TIMESTAMP(6), NULL FROM agent_runs r "
            + "WHERE r.id = #{runId} AND r.workspace_id = #{workspaceId} AND r.project_id = #{projectId}")
    int insertAgentStep(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId);

    @Update("UPDATE agent_steps SET status = 'SUCCEEDED', output_summary = #{summary}, "
            + "finished_at = UTC_TIMESTAMP(6) WHERE run_id = #{runId} AND step_no = 1 AND status = 'RUNNING' "
            + "AND EXISTS (SELECT 1 FROM agent_runs r WHERE r.id = agent_steps.run_id "
            + "AND r.workspace_id = #{workspaceId} AND r.project_id = #{projectId})")
    int completeAgentStep(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId,
            @Param("summary") String summary);

    @Update("UPDATE agent_runs SET status = 'SUCCEEDED', finished_at = UTC_TIMESTAMP(6), "
            + "last_sequence = #{lastSequence}, updated_at = UTC_TIMESTAMP(6), version = version + 1 "
            + "WHERE id = #{runId} AND workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND status = 'RUNNING'")
    int markSucceeded(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId,
            @Param("lastSequence") long lastSequence);

    @Update("UPDATE agent_runs SET status = 'FAILED', error_code = #{errorCode}, finished_at = UTC_TIMESTAMP(6), "
            + "last_sequence = #{lastSequence}, updated_at = UTC_TIMESTAMP(6), version = version + 1 "
            + "WHERE id = #{runId} AND workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND status IN ('QUEUED', 'RUNNING')")
    int markFailed(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId,
            @Param("lastSequence") long lastSequence,
            @Param("errorCode") String errorCode);
}
