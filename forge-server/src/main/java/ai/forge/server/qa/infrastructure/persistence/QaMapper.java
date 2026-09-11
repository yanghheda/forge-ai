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

    @Select("SELECT COUNT(*) FROM work_items WHERE id=#{requirementId} AND organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId} AND type='REQUIREMENT' AND status IN ('READY_FOR_QA','IN_QA') "
            + "AND deleted_at IS NULL")
    int countQaRequirement(@Param("organizationId") long organizationId,
            @Param("requirementId") long requirementId);

    @Insert("INSERT INTO test_cases (organization_id,work_item_id,title,preconditions,steps_json,"
            + "expected_result,priority,status,created_by,version,created_at,updated_at) VALUES "
            + "(#{organizationId},#{requirementId},#{title},#{preconditions},CAST(#{stepsJson} AS JSON),"
            + "#{expectedResult},#{priority},'ACTIVE',#{userId},0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertCase(@Param("row") Map<String, Object> row, @Param("organizationId") long organizationId, @Param("requirementId") long requirementId,
            @Param("title") String title, @Param("preconditions") String preconditions,
            @Param("stepsJson") String stepsJson, @Param("expectedResult") String expectedResult,
            @Param("priority") String priority, @Param("userId") long userId);

    @Select("SELECT id,work_item_id,title,preconditions,steps_json,expected_result,priority,version "
            + "FROM test_cases WHERE organization_id = #{organizationId} "
            + "AND work_item_id=#{requirementId} AND status='ACTIVE' ORDER BY id")
    List<Map<String, Object>> findCases(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Insert("INSERT INTO test_runs (organization_id,requirement_id,environment,status,started_by,"
            + "started_at,version) VALUES (#{organizationId},#{requirementId},#{environment},"
            + "'DRAFT',#{userId},UTC_TIMESTAMP(6),0)")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertRun(@Param("row") Map<String, Object> row, @Param("organizationId") long organizationId, @Param("requirementId") long requirementId,
            @Param("environment") String environment, @Param("userId") long userId);

    @Insert("INSERT INTO test_results (organization_id,test_run_id,test_case_id,status,actual_result,evidence_json,version) "
            + "SELECT organization_id,#{runId},id,'NOT_RUN','',JSON_ARRAY(),0 FROM test_cases "
            + "WHERE organization_id = #{organizationId} AND work_item_id=#{requirementId} "
            + "AND status='ACTIVE'")
    int snapshotResults(@Param("organizationId") long organizationId,
            @Param("requirementId") long requirementId, @Param("runId") long runId);

    @Select("SELECT id,requirement_id,environment,status,summary_json,started_at,finished_at,version "
            + "FROM test_runs WHERE id=#{runId} AND organization_id = #{organizationId}")
    List<Map<String, Object>> findRun(@Param("organizationId") long organizationId, @Param("runId") long runId);

    @Select("SELECT id,requirement_id,environment,status,summary_json,started_at,finished_at,version "
            + "FROM test_runs WHERE organization_id = #{organizationId} "
            + "AND requirement_id=#{requirementId} ORDER BY id DESC LIMIT 1")
    List<Map<String, Object>> findLatestRun(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Select("SELECT tr.id,tr.test_case_id,tc.title,tc.priority,tr.status,tr.actual_result,tr.evidence_json,"
            + "tr.executed_by,tr.executed_at,tr.version FROM test_results tr JOIN test_cases tc "
            + "ON tc.id=tr.test_case_id AND tc.organization_id=tr.organization_id JOIN test_runs run "
            + "ON run.id=tr.test_run_id AND run.organization_id=tr.organization_id "
            + "WHERE tr.organization_id=#{organizationId} AND run.organization_id=#{organizationId} AND tr.test_run_id=#{runId} "
            + "ORDER BY tr.id")
    List<Map<String, Object>> findResults(@Param("organizationId") long organizationId, @Param("runId") long runId);

    @Update("UPDATE test_results tr JOIN test_runs run ON run.id=tr.test_run_id "
            + "AND run.organization_id=tr.organization_id SET tr.status=#{status},tr.actual_result=#{actualResult},"
            + "tr.evidence_json=CAST(#{evidenceJson} AS JSON),tr.executed_by=#{userId},"
            + "tr.executed_at=UTC_TIMESTAMP(6),tr.version=tr.version+1,"
            + "run.status=IF(run.status='DRAFT','IN_PROGRESS',run.status),"
            + "run.version=run.version+IF(run.status='DRAFT',1,0) WHERE tr.id=#{resultId} "
            + "AND tr.organization_id=#{organizationId} AND tr.test_run_id=#{runId} AND run.organization_id=#{organizationId} "
            + "AND run.status IN ('DRAFT','IN_PROGRESS') AND tr.version=#{expectedVersion}")
    int updateResult(@Param("organizationId") long organizationId,
            @Param("runId") long runId, @Param("resultId") long resultId, @Param("userId") long userId,
            @Param("status") String status, @Param("actualResult") String actualResult,
            @Param("evidenceJson") String evidenceJson, @Param("expectedVersion") long expectedVersion);

    @Select("SELECT COUNT(*) total,SUM(tr.status='PASS') passed,SUM(tr.status='FAIL') failed,"
            + "SUM(tr.status='BLOCKED') blocked,SUM(tr.status='SKIPPED') skipped,"
            + "SUM(tr.status='NOT_RUN') not_run,"
            + "SUM(tr.status='SKIPPED' AND tc.priority IN ('P0','P1')) mandatory_skipped "
            + "FROM test_results tr JOIN test_cases tc ON tc.id=tr.test_case_id AND tc.organization_id=tr.organization_id "
            + "JOIN test_runs run ON run.id=tr.test_run_id AND run.organization_id=tr.organization_id "
            + "WHERE tr.organization_id=#{organizationId} AND run.organization_id=#{organizationId} AND tr.test_run_id=#{runId}")
    Map<String, Object> calculateSummary(@Param("organizationId") long organizationId, @Param("runId") long runId);

    @Update("UPDATE test_runs SET status='COMPLETED',summary_json=CAST(#{summaryJson} AS JSON),"
            + "finished_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=#{runId} AND organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId} AND status IN ('DRAFT','IN_PROGRESS') AND version=#{expectedVersion}")
    int completeRun(@Param("organizationId") long organizationId,
            @Param("runId") long runId, @Param("summaryJson") String summaryJson,
            @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE test_runs SET status='IN_PROGRESS',summary_json=NULL,finished_at=NULL,version=version+1 "
            + "WHERE id=#{runId} AND organization_id = #{organizationId} "
            + "AND status='COMPLETED' AND version=#{expectedVersion}")
    int reopenRun(@Param("organizationId") long organizationId,
            @Param("runId") long runId, @Param("expectedVersion") long expectedVersion);

    @Insert("INSERT INTO audit_logs (organization_id,actor_type,actor_id,action,resource_type,resource_id,"
            + "result,request_id,metadata_redacted_json,created_at) VALUES "
            + "(#{organizationId},'USER',#{userId},'QA_RUN_REOPEN','TEST_RUN',#{runId},'SUCCESS',"
            + "#{requestId},JSON_OBJECT('reason',#{reason}),UTC_TIMESTAMP(6))")
    int insertReopenAudit(@Param("organizationId") long organizationId,
            @Param("runId") long runId, @Param("userId") long userId, @Param("reason") String reason,
            @Param("requestId") String requestId);

    @Select("SELECT status,summary_json FROM test_runs WHERE organization_id = #{organizationId} "
            + "AND requirement_id=#{requirementId} ORDER BY id DESC LIMIT 1")
    List<Map<String, Object>> findLatestCompletedSummary(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);
}
