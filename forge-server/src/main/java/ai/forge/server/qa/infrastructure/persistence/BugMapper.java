package ai.forge.server.qa.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BugMapper {

    @Select("SELECT COUNT(*) FROM work_items WHERE id=#{requirementId} AND workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} AND type='REQUIREMENT' AND deleted_at IS NULL")
    int countRequirement(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("requirementId") long requirementId);

    @Select("SELECT COUNT(*) FROM test_results result JOIN test_runs run ON run.id=result.test_run_id "
            + "AND run.workspace_id=result.workspace_id WHERE result.id=#{resultId} "
            + "AND result.workspace_id=#{workspaceId} AND result.test_run_id=#{runId} "
            + "AND run.project_id=#{projectId} AND run.requirement_id=#{requirementId} "
            + "AND result.status IN ('FAIL','BLOCKED')")
    int countFailedResult(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("requirementId") long requirementId, @Param("runId") long runId,
            @Param("resultId") long resultId);

    @Select("SELECT COUNT(*) FROM work_items WHERE id=#{devTaskId} AND workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} AND type='DEV_TASK' AND deleted_at IS NULL")
    int countDevTask(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("devTaskId") long devTaskId);

    @Update("UPDATE work_items SET severity=#{severity} WHERE id=#{bugId} AND workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} AND type='BUG' AND status='OPEN'")
    int setSeverity(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("bugId") long bugId, @Param("severity") String severity);

    @Insert("INSERT INTO bug_details (work_item_id,workspace_id,project_id,requirement_id,test_run_id,"
            + "test_result_id,reproduction_steps_json,expected_result,actual_result,version) VALUES "
            + "(#{bugId},#{workspaceId},#{projectId},#{requirementId},#{runId},#{resultId},"
            + "CAST(#{stepsJson} AS JSON),#{expectedResult},#{actualResult},0)")
    int insertDetails(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("bugId") long bugId, @Param("requirementId") long requirementId,
            @Param("runId") Long runId, @Param("resultId") Long resultId,
            @Param("stepsJson") String stepsJson, @Param("expectedResult") String expectedResult,
            @Param("actualResult") String actualResult);

    @Insert("INSERT INTO work_item_relations (workspace_id,project_id,source_id,target_id,relation_type,created_by,"
            + "created_at) VALUES (#{workspaceId},#{projectId},#{sourceId},#{targetId},#{type},#{userId},"
            + "UTC_TIMESTAMP(6))")
    int insertRelation(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("sourceId") long sourceId, @Param("targetId") long targetId,
            @Param("type") String type, @Param("userId") long userId);

    @Select("SELECT item.id,item.item_key,item.title,item.status,item.severity,item.version,details.requirement_id,"
            + "details.test_run_id,details.test_result_id,details.reproduction_steps_json,details.expected_result,"
            + "details.actual_result,details.fix_note,details.fix_evidence_json FROM work_items item "
            + "JOIN bug_details details ON details.work_item_id=item.id AND details.workspace_id=item.workspace_id "
            + "WHERE item.id=#{bugId} AND item.workspace_id=#{workspaceId} AND item.project_id=#{projectId} "
            + "AND item.type='BUG' AND item.deleted_at IS NULL")
    List<Map<String, Object>> find(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("bugId") long bugId);

    @Select("SELECT item.id,item.item_key,item.title,item.status,item.severity,item.version,details.requirement_id,"
            + "details.test_run_id,details.test_result_id,details.reproduction_steps_json,details.expected_result,"
            + "details.actual_result,details.fix_note,details.fix_evidence_json FROM work_items item "
            + "JOIN bug_details details ON details.work_item_id=item.id AND details.workspace_id=item.workspace_id "
            + "WHERE item.workspace_id=#{workspaceId} AND item.project_id=#{projectId} "
            + "AND details.requirement_id=#{requirementId} AND item.deleted_at IS NULL ORDER BY item.id")
    List<Map<String, Object>> findByRequirement(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("requirementId") long requirementId);

    @Update("UPDATE work_items SET status=#{toStatus},updated_at=UTC_TIMESTAMP(6),version=version+1 "
            + "WHERE id=#{bugId} AND workspace_id=#{workspaceId} AND project_id=#{projectId} "
            + "AND type='BUG' AND status=#{fromStatus} AND version=#{expectedVersion}")
    int transition(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("bugId") long bugId, @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus, @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE bug_details SET fix_note=#{reason},fix_evidence_json=CAST(#{evidenceJson} AS JSON),"
            + "version=version+1 WHERE work_item_id=#{bugId} AND workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId}")
    int resolveDetails(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("bugId") long bugId, @Param("reason") String reason,
            @Param("evidenceJson") String evidenceJson);

    @Update("UPDATE bug_details SET verified_by=#{userId},verified_at=UTC_TIMESTAMP(6),version=version+1 "
            + "WHERE work_item_id=#{bugId} AND workspace_id=#{workspaceId} AND project_id=#{projectId}")
    int verifyDetails(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("bugId") long bugId, @Param("userId") long userId);

    @Update("UPDATE bug_details SET verified_by=NULL,verified_at=NULL,version=version+1 "
            + "WHERE work_item_id=#{bugId} AND workspace_id=#{workspaceId} AND project_id=#{projectId}")
    int reopenDetails(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("bugId") long bugId);

    @Insert("INSERT INTO work_item_events (workspace_id,project_id,work_item_id,event_type,from_status,to_status,"
            + "actor_type,actor_id,reason,metadata_json,idempotency_key,created_at) VALUES "
            + "(#{workspaceId},#{projectId},#{bugId},#{action},#{fromStatus},#{toStatus},'USER',#{userId},"
            + "#{reason},JSON_OBJECT(),#{idempotencyKey},UTC_TIMESTAMP(6))")
    int insertEvent(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("bugId") long bugId, @Param("action") String action,
            @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
            @Param("userId") long userId, @Param("reason") String reason,
            @Param("idempotencyKey") String idempotencyKey);
}
