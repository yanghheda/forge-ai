package ai.forge.server.workitem.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeliveryGraphMapper {

    @Select("SELECT id, parent_id, type, item_key, title, status FROM work_items "
            + "WHERE organization_id = #{organizationId} AND deleted_at IS NULL ORDER BY id")
    List<Map<String, Object>> findItems(
            @Param("organizationId") long organizationId);

    @Select("SELECT id, source_id, target_id, relation_type FROM work_item_relations "
            + "WHERE organization_id = #{organizationId} ORDER BY id")
    List<Map<String, Object>> findRelations(
            @Param("organizationId") long organizationId);

    @Select("SELECT id, work_item_id, type, title, status FROM documents "
            + "WHERE organization_id = #{organizationId} "
            + "AND work_item_id IS NOT NULL AND deleted_at IS NULL ORDER BY id")
    List<Map<String, Object>> findDocuments(
            @Param("organizationId") long organizationId);

    @Select("SELECT CONCAT('merge-request:', mr.id) node_id, CONCAT('work-item:', mr.work_item_id) parent_node_id, "
            + "mr.id resource_id, 'SOURCE_CONTROL' kind, 'MERGE_REQUEST' type, mr.title title, mr.state status, "
            + "'repo.read' required_permission FROM merge_requests mr JOIN git_repositories gr "
            + "ON gr.id = mr.repository_id WHERE mr.organization_id = #{organizationId} AND gr.organization_id = #{organizationId} "
            + "AND mr.work_item_id IS NOT NULL UNION ALL "
            + "SELECT CONCAT('pipeline:', pr.id), CONCAT('merge-request:', pr.merge_request_id), pr.id, "
            + "'SOURCE_CONTROL', 'PIPELINE', CONCAT('Pipeline #', pr.remote_pipeline_id), pr.status, 'repo.read' "
            + "FROM pipeline_runs pr JOIN git_repositories gr ON gr.id = pr.repository_id "
            + "WHERE pr.organization_id = #{organizationId} AND gr.organization_id = #{organizationId} "
            + "AND pr.merge_request_id IS NOT NULL UNION ALL "
            + "SELECT CONCAT('test-case:', tc.id), CONCAT('work-item:', tc.work_item_id), tc.id, "
            + "'QA', 'TEST_CASE', tc.title, tc.status, 'qa.read' FROM test_cases tc "
            + "WHERE tc.organization_id = #{organizationId} UNION ALL "
            + "SELECT CONCAT('test-run:', tr.id), CONCAT('work-item:', tr.requirement_id), tr.id, "
            + "'QA', 'TEST_RUN', CONCAT('Test Run #', tr.id), tr.status, 'qa.read' FROM test_runs tr "
            + "WHERE tr.organization_id = #{organizationId} UNION ALL "
            + "SELECT CONCAT('release:', r.id), CONCAT('work-item:', ri.work_item_id), r.id, "
            + "'RELEASE', 'RELEASE', r.version_name, r.status, 'release.read' FROM releases r "
            + "JOIN release_items ri ON ri.release_id = r.id AND ri.organization_id = r.organization_id "
            + "AND ri.organization_id = r.organization_id WHERE r.organization_id = #{organizationId} "
            + "UNION ALL SELECT CONCAT('deployment:', d.id), CONCAT('release:', d.release_id), d.id, "
            + "'RELEASE', 'DEPLOYMENT', CONCAT('Deployment #', d.id), d.status, 'release.read' FROM deployments d "
            + "WHERE d.organization_id = #{organizationId}")
    List<Map<String, Object>> findArtifacts(
            @Param("organizationId") long organizationId);
}
