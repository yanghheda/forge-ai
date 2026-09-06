package ai.forge.server.qa.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QaMapper {

    @Select("SELECT COUNT(*) FROM work_items WHERE id=#{requirementId} AND workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} AND type='REQUIREMENT' AND status IN ('READY_FOR_QA','IN_QA') "
            + "AND deleted_at IS NULL")
    int countQaRequirement(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("requirementId") long requirementId);

    @Insert("INSERT INTO test_cases (workspace_id,project_id,work_item_id,title,preconditions,steps_json,"
            + "expected_result,priority,status,created_by,version,created_at,updated_at) VALUES "
            + "(#{workspaceId},#{projectId},#{requirementId},#{title},#{preconditions},CAST(#{stepsJson} AS JSON),"
            + "#{expectedResult},#{priority},'ACTIVE',#{userId},0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertCase(@Param("row") Map<String, Object> row, @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("requirementId") long requirementId,
            @Param("title") String title, @Param("preconditions") String preconditions,
            @Param("stepsJson") String stepsJson, @Param("expectedResult") String expectedResult,
            @Param("priority") String priority, @Param("userId") long userId);

    @Select("SELECT id,work_item_id,title,preconditions,steps_json,expected_result,priority,version "
            + "FROM test_cases WHERE workspace_id=#{workspaceId} AND project_id=#{projectId} "
            + "AND work_item_id=#{requirementId} AND status='ACTIVE' ORDER BY id")
    List<Map<String, Object>> findCases(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("requirementId") long requirementId);

    @Insert("INSERT INTO test_runs (workspace_id,project_id,requirement_id,environment,status,started_by,"
            + "started_at,version) VALUES (#{workspaceId},#{projectId},#{requirementId},#{environment},"
            + "'DRAFT',#{userId},UTC_TIMESTAMP(6),0)")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertRun(@Param("row") Map<String, Object> row, @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("requirementId") long requirementId,
            @Param("environment") String environment, @Param("userId") long userId);

    @Insert("INSERT INTO test_results (workspace_id,test_run_id,test_case_id,status,actual_result,evidence_json,version) "
            + "SELECT workspace_id,#{runId},id,'NOT_RUN','',JSON_ARRAY(),0 FROM test_cases "
            + "WHERE workspace_id=#{workspaceId} AND project_id=#{projectId} AND work_item_id=#{requirementId} "
            + "AND status='ACTIVE'")
    int snapshotResults(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("requirementId") long requirementId, @Param("runId") long runId);

    @Select("SELECT id,requirement_id,environment,status,summary_json,started_at,finished_at,version "
            + "FROM test_runs WHERE id=#{runId} AND workspace_id=#{workspaceId} AND project_id=#{projectId}")
    List<Map<String, Object>> findRun(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("runId") long runId);

    @Select("SELECT id,requirement_id,environment,status,summary_json,started_at,finished_at,version "
            + "FROM test_runs WHERE workspace_id=#{workspaceId} AND project_id=#{projectId} "
            + "AND requirement_id=#{requirementId} ORDER BY id DESC LIMIT 1")
    List<Map<String, Object>> findLatestRun(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("requirementId") long requirementId);

    @Select("SELECT tr.id,tr.test_case_id,tc.title,tc.priority,tr.status,tr.actual_result,tr.evidence_json,"
            + "tr.executed_by,tr.executed_at,tr.version FROM test_results tr JOIN test_cases tc "
            + "ON tc.id=tr.test_case_id AND tc.workspace_id=tr.workspace_id JOIN test_runs run "
            + "ON run.id=tr.test_run_id AND run.workspace_id=tr.workspace_id "
            + "WHERE tr.workspace_id=#{workspaceId} AND run.project_id=#{projectId} AND tr.test_run_id=#{runId} "
            + "ORDER BY tr.id")
    List<Map<String, Object>> findResults(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("runId") long runId);

    @Update("UPDATE test_results tr JOIN test_runs run ON run.id=tr.test_run_id "
            + "AND run.workspace_id=tr.workspace_id SET tr.status=#{status},tr.actual_result=#{actualResult},"
            + "tr.evidence_json=CAST(#{evidenceJson} AS JSON),tr.executed_by=#{userId},"
            + "tr.executed_at=UTC_TIMESTAMP(6),tr.version=tr.version+1,"
            + "run.status=IF(run.status='DRAFT','IN_PROGRESS',run.status),"
            + "run.version=run.version+IF(run.status='DRAFT',1,0) WHERE tr.id=#{resultId} "
            + "AND tr.workspace_id=#{workspaceId} AND tr.test_run_id=#{runId} AND run.project_id=#{projectId} "
            + "AND run.status IN ('DRAFT','IN_PROGRESS') AND tr.version=#{expectedVersion}")
    int updateResult(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("runId") long runId, @Param("resultId") long resultId, @Param("userId") long userId,
            @Param("status") String status, @Param("actualResult") String actualResult,
            @Param("evidenceJson") String evidenceJson, @Param("expectedVersion") long expectedVersion);

    @Select("SELECT COUNT(*) total,SUM(tr.status='PASS') passed,SUM(tr.status='FAIL') failed,"
            + "SUM(tr.status='BLOCKED') blocked,SUM(tr.status='SKIPPED') skipped,"
            + "SUM(tr.status='NOT_RUN') not_run,"
            + "SUM(tr.status='SKIPPED' AND tc.priority IN ('P0','P1')) mandatory_skipped "
            + "FROM test_results tr JOIN test_cases tc ON tc.id=tr.test_case_id AND tc.workspace_id=tr.workspace_id "
            + "JOIN test_runs run ON run.id=tr.test_run_id AND run.workspace_id=tr.workspace_id "
            + "WHERE tr.workspace_id=#{workspaceId} AND run.project_id=#{projectId} AND tr.test_run_id=#{runId}")
    Map<String, Object> calculateSummary(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("runId") long runId);

    @Update("UPDATE test_runs SET status='COMPLETED',summary_json=CAST(#{summaryJson} AS JSON),"
            + "finished_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=#{runId} AND workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} AND status IN ('DRAFT','IN_PROGRESS') AND version=#{expectedVersion}")
    int completeRun(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("runId") long runId, @Param("summaryJson") String summaryJson,
            @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE test_runs SET status='IN_PROGRESS',summary_json=NULL,finished_at=NULL,version=version+1 "
            + "WHERE id=#{runId} AND workspace_id=#{workspaceId} AND project_id=#{projectId} "
            + "AND status='COMPLETED' AND version=#{expectedVersion}")
    int reopenRun(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("runId") long runId, @Param("expectedVersion") long expectedVersion);

    @Insert("INSERT INTO audit_logs (workspace_id,project_id,actor_type,actor_id,action,resource_type,resource_id,"
            + "result,request_id,metadata_redacted_json,created_at) VALUES "
            + "(#{workspaceId},#{projectId},'USER',#{userId},'QA_RUN_REOPEN','TEST_RUN',#{runId},'SUCCESS',"
            + "#{requestId},JSON_OBJECT('reason',#{reason}),UTC_TIMESTAMP(6))")
    int insertReopenAudit(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("runId") long runId, @Param("userId") long userId, @Param("reason") String reason,
            @Param("requestId") String requestId);

    @Select("SELECT status,summary_json FROM test_runs WHERE workspace_id=#{workspaceId} AND project_id=#{projectId} "
            + "AND requirement_id=#{requirementId} ORDER BY id DESC LIMIT 1")
    List<Map<String, Object>> findLatestCompletedSummary(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("requirementId") long requirementId);
}
