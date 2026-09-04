package ai.forge.server.agent.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentToolCallMapper {

    @Insert("INSERT INTO agent_tool_calls (run_id, workspace_id, project_id, tool_call_id, tool_name, "
            + "tool_version, risk_level, status, arguments_json, result_json, idempotency_key, created_at, "
            + "finished_at) VALUES (#{runId}, #{workspaceId}, #{projectId}, #{toolCallId}, #{toolName}, "
            + "#{toolVersion}, #{riskLevel}, 'SUCCEEDED', CAST(#{argumentsJson} AS JSON), "
            + "CAST(#{resultJson} AS JSON), #{idempotencyKey}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    int insertToolCall(
            @Param("runId") String runId,
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("toolCallId") String toolCallId,
            @Param("toolName") String toolName,
            @Param("toolVersion") int toolVersion,
            @Param("riskLevel") String riskLevel,
            @Param("argumentsJson") String argumentsJson,
            @Param("resultJson") String resultJson,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT tool_name, tool_version, arguments_json, result_json FROM agent_tool_calls "
            + "WHERE idempotency_key = #{idempotencyKey} "
            + "AND run_id = #{runId} AND workspace_id = #{workspaceId} AND project_id = #{projectId} "
            + "AND status = 'SUCCEEDED'")
    List<Map<String, Object>> findSuccessful(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("runId") String runId,
            @Param("idempotencyKey") String idempotencyKey);
}
