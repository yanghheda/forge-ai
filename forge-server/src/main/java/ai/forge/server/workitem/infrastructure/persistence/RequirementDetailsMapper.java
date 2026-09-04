package ai.forge.server.workitem.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RequirementDetailsMapper {

    @Select("SELECT d.work_item_id, d.workspace_id, d.goal, d.in_scope, d.out_of_scope, "
            + "d.acceptance_criteria_json, d.business_value, d.version, d.updated_at "
            + "FROM requirement_details d JOIN work_items w ON w.id=d.work_item_id "
            + "WHERE d.workspace_id=#{workspaceId} AND w.workspace_id=#{workspaceId} "
            + "AND w.project_id=#{projectId} AND d.work_item_id=#{workItemId} "
            + "AND w.type='REQUIREMENT' AND w.deleted_at IS NULL")
    List<Map<String, Object>> find(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("workItemId") long workItemId);

    @Insert("INSERT INTO requirement_details (work_item_id, workspace_id, goal, in_scope, out_of_scope, "
            + "acceptance_criteria_json, business_value, updated_at, version) "
            + "SELECT id, workspace_id, #{goal}, #{inScope}, #{outOfScope}, CAST(#{criteriaJson} AS JSON), "
            + "#{businessValue}, UTC_TIMESTAMP(6), 1 FROM work_items WHERE id=#{workItemId} "
            + "AND workspace_id=#{workspaceId} AND project_id=#{projectId} AND type='REQUIREMENT' "
            + "AND deleted_at IS NULL AND #{expectedVersion}=0")
    int insert(
            @Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("workItemId") long workItemId, @Param("goal") String goal,
            @Param("inScope") String inScope, @Param("outOfScope") String outOfScope,
            @Param("criteriaJson") String criteriaJson, @Param("businessValue") String businessValue,
            @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE requirement_details d JOIN work_items w ON w.id=d.work_item_id "
            + "SET d.goal=#{goal}, d.in_scope=#{inScope}, d.out_of_scope=#{outOfScope}, "
            + "d.acceptance_criteria_json=CAST(#{criteriaJson} AS JSON), d.business_value=#{businessValue}, "
            + "d.updated_at=UTC_TIMESTAMP(6), d.version=d.version+1 WHERE d.work_item_id=#{workItemId} "
            + "AND d.workspace_id=#{workspaceId} AND w.workspace_id=#{workspaceId} AND w.project_id=#{projectId} "
            + "AND w.type='REQUIREMENT' AND w.deleted_at IS NULL AND d.version=#{expectedVersion}")
    int update(
            @Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("workItemId") long workItemId, @Param("goal") String goal,
            @Param("inScope") String inScope, @Param("outOfScope") String outOfScope,
            @Param("criteriaJson") String criteriaJson, @Param("businessValue") String businessValue,
            @Param("expectedVersion") long expectedVersion);
}
