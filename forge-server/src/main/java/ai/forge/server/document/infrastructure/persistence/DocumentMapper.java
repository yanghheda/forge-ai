package ai.forge.server.document.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DocumentMapper {
    @Select("SELECT COUNT(*) FROM work_items WHERE id=#{workItemId} AND organization_id=#{organizationId} "
            + "AND organization_id=#{organizationId} AND type='REQUIREMENT' AND deleted_at IS NULL")
    int countRequirement(@Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId);
    @Insert("INSERT INTO documents (organization_id, work_item_id, type, title, status, visibility, current_version_id, created_by, created_at, updated_at, deleted_at, version) VALUES (#{organizationId}, #{workItemId}, #{type}, #{title}, 'DRAFT', 'ORGANIZATION', NULL, #{userId}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)")
    int insertDocument(@Param("organizationId") long organizationId,
            @Param("workItemId") long workItemId, @Param("userId") long userId,
            @Param("type") String type, @Param("title") String title);
    @Select("SELECT LAST_INSERT_ID()") long lastInsertId();
    @Select("SELECT id, organization_id, work_item_id, type, title, status, current_version_id, version, created_at, updated_at FROM documents WHERE id=#{documentId} AND organization_id = #{organizationId} AND deleted_at IS NULL")
    List<Map<String, Object>> findDocument(@Param("organizationId") long organizationId, @Param("documentId") long documentId);
    @Select("SELECT id, organization_id, work_item_id, type, title, status, current_version_id, version, created_at, updated_at FROM documents WHERE organization_id = #{organizationId} AND work_item_id=#{workItemId} AND deleted_at IS NULL ORDER BY id")
    List<Map<String, Object>> findByWorkItem(@Param("organizationId") long organizationId, @Param("workItemId") long workItemId);
    @Select("SELECT id, document_id, version_no, content, plain_text, content_hash, created_by, created_at FROM document_versions WHERE organization_id=#{organizationId} AND document_id=#{documentId} ORDER BY version_no DESC")
    List<Map<String, Object>> findVersions(@Param("organizationId") long organizationId, @Param("documentId") long documentId);
    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM document_versions WHERE document_id=#{documentId} FOR UPDATE")
    long nextVersionNo(@Param("documentId") long documentId);
    @Insert("INSERT INTO document_versions (organization_id, document_id, version_no, content_format, content, content_hash, plain_text, summary, created_by, created_at) VALUES (#{organizationId}, #{documentId}, #{versionNo}, 'PROSEMIRROR_JSON', CAST(#{content} AS JSON), #{hash}, #{plainText}, NULL, #{userId}, UTC_TIMESTAMP(6))")
    int insertVersion(@Param("organizationId") long organizationId, @Param("documentId") long documentId, @Param("versionNo") long versionNo, @Param("content") String content, @Param("plainText") String plainText, @Param("hash") String hash, @Param("userId") long userId);
    @Update("UPDATE documents SET current_version_id=#{versionId}, updated_at=UTC_TIMESTAMP(6), version=version+1 WHERE id=#{documentId} AND organization_id = #{organizationId} AND deleted_at IS NULL AND version=#{expectedVersion}")
    int setCurrentVersion(@Param("organizationId") long organizationId, @Param("documentId") long documentId, @Param("versionId") long versionId, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE documents SET current_version_id=#{versionId}, status='PUBLISHED', updated_at=UTC_TIMESTAMP(6), version=version+1 WHERE id=#{documentId} AND organization_id = #{organizationId} AND deleted_at IS NULL AND version=#{expectedVersion} AND EXISTS (SELECT 1 FROM document_versions WHERE id=#{versionId} AND document_id=#{documentId} AND organization_id=#{organizationId})")
    int publish(@Param("organizationId") long organizationId, @Param("documentId") long documentId, @Param("versionId") long versionId, @Param("expectedVersion") long expectedVersion);
    @Insert("INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at, processed_at) VALUES ('DOCUMENT', #{documentId}, 'DOCUMENT_VERSION_PUBLISHED', JSON_OBJECT('organizationId', #{organizationId}, 'organizationId', #{organizationId}, 'documentId', #{documentId}, 'versionId', #{versionId}), UTC_TIMESTAMP(6), NULL)")
    int insertPublishedEvent(@Param("organizationId") long organizationId, @Param("documentId") long documentId, @Param("versionId") long versionId);
}
