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
    @Insert("INSERT INTO documents (workspace_id, project_id, work_item_id, type, title, status, visibility, current_version_id, created_by, created_at, updated_at, deleted_at, version) VALUES (#{workspaceId}, #{projectId}, NULL, #{type}, #{title}, 'DRAFT', 'PROJECT', NULL, #{userId}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)")
    int insertDocument(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("userId") long userId, @Param("type") String type, @Param("title") String title);
    @Select("SELECT LAST_INSERT_ID()") long lastInsertId();
    @Select("SELECT id, workspace_id, project_id, type, title, status, current_version_id, version, created_at, updated_at FROM documents WHERE id=#{documentId} AND workspace_id=#{workspaceId} AND project_id=#{projectId} AND deleted_at IS NULL")
    List<Map<String, Object>> findDocument(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("documentId") long documentId);
    @Select("SELECT id, document_id, version_no, content, plain_text, content_hash, created_by, created_at FROM document_versions WHERE workspace_id=#{workspaceId} AND document_id=#{documentId} ORDER BY version_no DESC")
    List<Map<String, Object>> findVersions(@Param("workspaceId") long workspaceId, @Param("documentId") long documentId);
    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM document_versions WHERE document_id=#{documentId} FOR UPDATE")
    long nextVersionNo(@Param("documentId") long documentId);
    @Insert("INSERT INTO document_versions (workspace_id, document_id, version_no, content_format, content, content_hash, plain_text, summary, created_by, created_at) VALUES (#{workspaceId}, #{documentId}, #{versionNo}, 'PROSEMIRROR_JSON', CAST(#{content} AS JSON), #{hash}, #{plainText}, NULL, #{userId}, UTC_TIMESTAMP(6))")
    int insertVersion(@Param("workspaceId") long workspaceId, @Param("documentId") long documentId, @Param("versionNo") long versionNo, @Param("content") String content, @Param("plainText") String plainText, @Param("hash") String hash, @Param("userId") long userId);
    @Update("UPDATE documents SET current_version_id=#{versionId}, updated_at=UTC_TIMESTAMP(6), version=version+1 WHERE id=#{documentId} AND workspace_id=#{workspaceId} AND project_id=#{projectId} AND deleted_at IS NULL AND version=#{expectedVersion}")
    int setCurrentVersion(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("documentId") long documentId, @Param("versionId") long versionId, @Param("expectedVersion") long expectedVersion);
    @Update("UPDATE documents SET current_version_id=#{versionId}, status='PUBLISHED', updated_at=UTC_TIMESTAMP(6), version=version+1 WHERE id=#{documentId} AND workspace_id=#{workspaceId} AND project_id=#{projectId} AND deleted_at IS NULL AND version=#{expectedVersion} AND EXISTS (SELECT 1 FROM document_versions WHERE id=#{versionId} AND document_id=#{documentId} AND workspace_id=#{workspaceId})")
    int publish(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("documentId") long documentId, @Param("versionId") long versionId, @Param("expectedVersion") long expectedVersion);
    @Insert("INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at, processed_at) VALUES ('DOCUMENT', #{documentId}, 'DOCUMENT_VERSION_PUBLISHED', JSON_OBJECT('workspaceId', #{workspaceId}, 'projectId', #{projectId}, 'documentId', #{documentId}, 'versionId', #{versionId}), UTC_TIMESTAMP(6), NULL)")
    int insertPublishedEvent(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId, @Param("documentId") long documentId, @Param("versionId") long versionId);
}
