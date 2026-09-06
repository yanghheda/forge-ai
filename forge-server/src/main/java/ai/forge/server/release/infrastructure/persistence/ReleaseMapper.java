package ai.forge.server.release.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReleaseMapper {

    @Insert("INSERT INTO releases (workspace_id,project_id,version_name,environment,status,policy_snapshot_json,"
            + "created_by,created_at,updated_at,version) VALUES (#{workspaceId},#{projectId},#{versionName},"
            + "#{environment},'DRAFT',CAST(#{policyJson} AS JSON),#{userId},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Map<String, Object> row, @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("versionName") String versionName,
            @Param("environment") String environment, @Param("policyJson") String policyJson,
            @Param("userId") long userId);

    @Select("<script>SELECT id FROM work_items WHERE workspace_id=#{workspaceId} AND project_id=#{projectId} "
            + "AND type='REQUIREMENT' AND deleted_at IS NULL AND id IN "
            + "<foreach item='id' collection='itemIds' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> findScopedRequirementIds(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("itemIds") List<Long> itemIds);

    @Insert("INSERT INTO release_items (release_id,work_item_id,workspace_id,project_id) "
            + "VALUES (#{releaseId},#{itemId},#{workspaceId},#{projectId})")
    int insertItem(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("releaseId") long releaseId, @Param("itemId") long itemId);

    @Select("SELECT id,workspace_id,project_id,version_name,environment,status,release_note,policy_snapshot_json,"
            + "created_at,updated_at,version FROM releases WHERE id=#{releaseId} AND workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId}")
    List<Map<String, Object>> find(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("releaseId") long releaseId);

    @Select("SELECT id,workspace_id,project_id,version_name,environment,status,release_note,policy_snapshot_json,"
            + "created_at,updated_at,version FROM releases WHERE workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} ORDER BY id DESC")
    List<Map<String, Object>> list(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Select("SELECT work_item_id FROM release_items WHERE release_id=#{releaseId} AND workspace_id=#{workspaceId} "
            + "AND project_id=#{projectId} ORDER BY work_item_id")
    List<Long> findItemIds(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("releaseId") long releaseId);

    @Update("UPDATE releases SET release_note=#{note},status='DRAFT',updated_at=UTC_TIMESTAMP(6),version=version+1 "
            + "WHERE id=#{releaseId} AND workspace_id=#{workspaceId} AND project_id=#{projectId} "
            + "AND version=#{expectedVersion}")
    int updateNote(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("releaseId") long releaseId, @Param("note") String note,
            @Param("expectedVersion") long expectedVersion);

    @Select("SELECT item.id,item.item_key,item.status,item.version FROM release_items ri JOIN work_items item "
            + "ON item.id=ri.work_item_id AND item.workspace_id=ri.workspace_id AND item.project_id=ri.project_id "
            + "WHERE ri.release_id=#{releaseId} AND ri.workspace_id=#{workspaceId} AND ri.project_id=#{projectId} "
            + "ORDER BY item.id")
    List<Map<String, Object>> findItemFacts(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("releaseId") long releaseId);

    @Select("SELECT requirement.item_key,COALESCE(mr.web_url,'MISSING_MR') mr_ref,COALESCE(mr.head_sha,'') head_sha,"
            + "COALESCE(p.commit_sha,'') pipeline_sha,COALESCE(p.status,'MISSING') pipeline_status,"
            + "COALESCE(p.id,0) pipeline_version FROM release_items ri JOIN work_items requirement "
            + "ON requirement.id=ri.work_item_id AND requirement.workspace_id=ri.workspace_id "
            + "LEFT JOIN work_items dev ON dev.parent_id=requirement.id AND dev.workspace_id=requirement.workspace_id "
            + "AND dev.project_id=requirement.project_id AND dev.type='DEV_TASK' AND dev.deleted_at IS NULL "
            + "LEFT JOIN merge_requests mr ON mr.work_item_id=dev.id AND mr.workspace_id=dev.workspace_id "
            + "LEFT JOIN pipeline_runs p ON p.id=(SELECT p2.id FROM pipeline_runs p2 WHERE p2.workspace_id=ri.workspace_id "
            + "AND p2.merge_request_id=mr.id ORDER BY p2.remote_updated_at DESC,p2.id DESC LIMIT 1) "
            + "WHERE ri.release_id=#{releaseId} AND ri.workspace_id=#{workspaceId} AND ri.project_id=#{projectId} "
            + "ORDER BY requirement.id,dev.id")
    List<Map<String, Object>> findPipelineFacts(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("releaseId") long releaseId);

    @Select("SELECT item.item_key,COALESCE(run.id,0) run_id,COALESCE(run.status,'MISSING') run_status,"
            + "COALESCE(JSON_EXTRACT(run.summary_json,'$.passed'),0) passed,"
            + "COALESCE(JSON_EXTRACT(run.summary_json,'$.failed'),0) failed,"
            + "COALESCE(JSON_EXTRACT(run.summary_json,'$.blocked'),0) blocked,COALESCE(run.version,0) run_version "
            + "FROM release_items ri JOIN work_items item ON item.id=ri.work_item_id AND item.workspace_id=ri.workspace_id "
            + "LEFT JOIN test_runs run ON run.id=(SELECT tr.id FROM test_runs tr WHERE tr.workspace_id=ri.workspace_id "
            + "AND tr.project_id=ri.project_id AND tr.requirement_id=ri.work_item_id ORDER BY tr.id DESC LIMIT 1) "
            + "WHERE ri.release_id=#{releaseId} AND ri.workspace_id=#{workspaceId} AND ri.project_id=#{projectId} "
            + "ORDER BY item.id")
    List<Map<String, Object>> findQaFacts(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("releaseId") long releaseId);

    @Select("SELECT bug.item_key,bug.severity,bug.status,bug.version FROM release_items ri "
            + "JOIN bug_details details ON details.requirement_id=ri.work_item_id "
            + "AND details.workspace_id=ri.workspace_id AND details.project_id=ri.project_id "
            + "JOIN work_items bug ON bug.id=details.work_item_id AND bug.workspace_id=details.workspace_id "
            + "WHERE ri.release_id=#{releaseId} AND ri.workspace_id=#{workspaceId} AND ri.project_id=#{projectId} "
            + "AND bug.deleted_at IS NULL ORDER BY bug.id")
    List<Map<String, Object>> findBugFacts(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("releaseId") long releaseId);

    @Select("SELECT EXISTS(SELECT 1 FROM workspace_members wm JOIN member_roles mr ON mr.workspace_member_id=wm.id "
            + "JOIN roles role ON role.id=mr.role_id WHERE wm.workspace_id=#{workspaceId} AND wm.status='ACTIVE' "
            + "AND role.code='RELEASE_APPROVER' AND (mr.project_id IS NULL OR mr.project_id=#{projectId}))")
    boolean approverAvailable(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Select("SELECT version FROM project_policies WHERE workspace_id=#{workspaceId} AND project_id=#{projectId}")
    Long policyVersion(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Insert("INSERT INTO release_prechecks (workspace_id,release_id,status,checks_json,checked_at,checked_by_type,"
            + "checked_by_id,resource_versions_json) VALUES (#{workspaceId},#{releaseId},#{status},"
            + "CAST(#{checksJson} AS JSON),UTC_TIMESTAMP(6),#{checkedByType},#{checkedById},"
            + "CAST(#{versionsJson} AS JSON))")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertPrecheck(@Param("row") Map<String, Object> row, @Param("workspaceId") long workspaceId,
            @Param("releaseId") long releaseId, @Param("status") String status,
            @Param("checksJson") String checksJson, @Param("checkedByType") String checkedByType,
            @Param("checkedById") long checkedById, @Param("versionsJson") String versionsJson);

    @Update("UPDATE releases SET status='PRECHECKED',updated_at=UTC_TIMESTAMP(6) WHERE id=#{releaseId} "
            + "AND workspace_id=#{workspaceId} AND project_id=#{projectId}")
    int markPrechecked(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId,
            @Param("releaseId") long releaseId);

    @Select("SELECT id,status,checks_json,resource_versions_json,checked_by_type,checked_by_id,checked_at "
            + "FROM release_prechecks WHERE workspace_id=#{workspaceId} AND release_id=#{releaseId} "
            + "ORDER BY id DESC LIMIT 1")
    List<Map<String, Object>> findLatestPrecheck(@Param("workspaceId") long workspaceId,
            @Param("releaseId") long releaseId);
}
