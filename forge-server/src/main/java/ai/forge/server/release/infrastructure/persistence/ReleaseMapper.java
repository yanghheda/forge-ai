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

    @Insert("INSERT INTO releases (organization_id,version_name,environment,status,policy_snapshot_json,"
            + "created_by,created_at,updated_at,version) VALUES (#{organizationId},#{versionName},"
            + "#{environment},'DRAFT',CAST(#{policyJson} AS JSON),#{userId},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Map<String, Object> row, @Param("organizationId") long organizationId, @Param("versionName") String versionName,
            @Param("environment") String environment, @Param("policyJson") String policyJson,
            @Param("userId") long userId);

    @Select("<script>SELECT id FROM work_items WHERE organization_id = #{organizationId} "
            + "AND type='REQUIREMENT' AND deleted_at IS NULL AND id IN "
            + "<foreach item='id' collection='itemIds' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> findScopedRequirementIds(@Param("organizationId") long organizationId, @Param("itemIds") List<Long> itemIds);

    @Insert("INSERT INTO release_items (release_id,work_item_id,organization_id) "
            + "VALUES (#{releaseId},#{itemId},#{organizationId})")
    int insertItem(@Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId, @Param("itemId") long itemId);

    @Select("SELECT id,organization_id,version_name,environment,status,release_note,policy_snapshot_json,"
            + "created_at,updated_at,version FROM releases WHERE id=#{releaseId} AND organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId}")
    List<Map<String, Object>> find(@Param("organizationId") long organizationId, @Param("releaseId") long releaseId);

    @Select("SELECT id,organization_id,version_name,environment,status,release_note,policy_snapshot_json,"
            + "created_at,updated_at,version FROM releases WHERE organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId} ORDER BY id DESC")
    List<Map<String, Object>> list(@Param("organizationId") long organizationId);

    @Select("SELECT work_item_id FROM release_items WHERE release_id=#{releaseId} AND organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId} ORDER BY work_item_id")
    List<Long> findItemIds(@Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId);

    @Update("UPDATE releases SET release_note=#{note},status='DRAFT',updated_at=UTC_TIMESTAMP(6),version=version+1 "
            + "WHERE id=#{releaseId} AND organization_id = #{organizationId} "
            + "AND version=#{expectedVersion}")
    int updateNote(@Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId, @Param("note") String note,
            @Param("expectedVersion") long expectedVersion);

    @Select("SELECT item.id,item.item_key,item.status,item.version FROM release_items ri JOIN work_items item "
            + "ON item.id=ri.work_item_id AND item.organization_id=ri.organization_id "
            + "WHERE ri.release_id=#{releaseId} AND ri.organization_id=#{organizationId} "
            + "ORDER BY item.id")
    List<Map<String, Object>> findItemFacts(@Param("organizationId") long organizationId, @Param("releaseId") long releaseId);

    @Select("SELECT requirement.item_key,COALESCE(mr.web_url,'MISSING_MR') mr_ref,COALESCE(mr.head_sha,'') head_sha,"
            + "COALESCE(p.commit_sha,'') pipeline_sha,COALESCE(p.status,'MISSING') pipeline_status,"
            + "COALESCE(p.id,0) pipeline_version FROM release_items ri JOIN work_items requirement "
            + "ON requirement.id=ri.work_item_id AND requirement.organization_id=ri.organization_id "
            + "LEFT JOIN work_items dev ON dev.parent_id=requirement.id AND dev.organization_id=requirement.organization_id "
            + "AND dev.organization_id=requirement.organization_id AND dev.type='DEV_TASK' AND dev.deleted_at IS NULL "
            + "LEFT JOIN merge_requests mr ON mr.work_item_id=dev.id AND mr.organization_id=dev.organization_id "
            + "LEFT JOIN pipeline_runs p ON p.id=(SELECT p2.id FROM pipeline_runs p2 WHERE p2.organization_id=ri.organization_id "
            + "AND p2.merge_request_id=mr.id ORDER BY p2.remote_updated_at DESC,p2.id DESC LIMIT 1) "
            + "WHERE ri.release_id=#{releaseId} AND ri.organization_id=#{organizationId} "
            + "ORDER BY requirement.id,dev.id")
    List<Map<String, Object>> findPipelineFacts(@Param("organizationId") long organizationId, @Param("releaseId") long releaseId);

    @Select("SELECT item.item_key,COALESCE(run.id,0) run_id,COALESCE(run.status,'MISSING') run_status,"
            + "COALESCE(JSON_EXTRACT(run.summary_json,'$.passed'),0) passed,"
            + "COALESCE(JSON_EXTRACT(run.summary_json,'$.failed'),0) failed,"
            + "COALESCE(JSON_EXTRACT(run.summary_json,'$.blocked'),0) blocked,COALESCE(run.version,0) run_version "
            + "FROM release_items ri JOIN work_items item ON item.id=ri.work_item_id AND item.organization_id=ri.organization_id "
            + "LEFT JOIN test_runs run ON run.id=(SELECT tr.id FROM test_runs tr WHERE tr.organization_id=ri.organization_id "
            + "AND tr.organization_id=ri.organization_id AND tr.requirement_id=ri.work_item_id ORDER BY tr.id DESC LIMIT 1) "
            + "WHERE ri.release_id=#{releaseId} AND ri.organization_id=#{organizationId} "
            + "ORDER BY item.id")
    List<Map<String, Object>> findQaFacts(@Param("organizationId") long organizationId, @Param("releaseId") long releaseId);

    @Select("SELECT bug.item_key,bug.severity,bug.status,bug.version FROM release_items ri "
            + "JOIN bug_details details ON details.requirement_id=ri.work_item_id "
            + "AND details.organization_id=ri.organization_id "
            + "JOIN work_items bug ON bug.id=details.work_item_id AND bug.organization_id=details.organization_id "
            + "WHERE ri.release_id=#{releaseId} AND ri.organization_id=#{organizationId} "
            + "AND bug.deleted_at IS NULL ORDER BY bug.id")
    List<Map<String, Object>> findBugFacts(@Param("organizationId") long organizationId, @Param("releaseId") long releaseId);

    @Select("SELECT EXISTS(SELECT 1 FROM organization_members om "
            + "JOIN member_roles mr ON mr.organization_member_id=om.id "
            + "JOIN roles role ON role.id=mr.role_id WHERE om.organization_id=#{organizationId} "
            + "AND om.status='ACTIVE' AND role.code='RELEASE_APPROVER')")
    boolean approverAvailable(@Param("organizationId") long organizationId);

    @Select("SELECT version FROM organization_policies WHERE organization_id = #{organizationId}")
    Long policyVersion(@Param("organizationId") long organizationId);

    @Insert("INSERT INTO release_prechecks (organization_id,release_id,status,checks_json,checked_at,checked_by_type,"
            + "checked_by_id,resource_versions_json) VALUES (#{organizationId},#{releaseId},#{status},"
            + "CAST(#{checksJson} AS JSON),UTC_TIMESTAMP(6),#{checkedByType},#{checkedById},"
            + "CAST(#{versionsJson} AS JSON))")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertPrecheck(@Param("row") Map<String, Object> row, @Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId, @Param("status") String status,
            @Param("checksJson") String checksJson, @Param("checkedByType") String checkedByType,
            @Param("checkedById") long checkedById, @Param("versionsJson") String versionsJson);

    @Update("UPDATE releases SET status='PRECHECKED',updated_at=UTC_TIMESTAMP(6) WHERE id=#{releaseId} "
            + "AND organization_id = #{organizationId}")
    int markPrechecked(@Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId);

    @Select("SELECT id,status,checks_json,resource_versions_json,checked_by_type,checked_by_id,checked_at "
            + "FROM release_prechecks WHERE organization_id=#{organizationId} AND release_id=#{releaseId} "
            + "ORDER BY id DESC LIMIT 1")
    List<Map<String, Object>> findLatestPrecheck(@Param("organizationId") long organizationId,
            @Param("releaseId") long releaseId);
}
