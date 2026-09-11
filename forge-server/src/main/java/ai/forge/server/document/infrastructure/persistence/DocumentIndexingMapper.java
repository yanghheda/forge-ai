package ai.forge.server.document.infrastructure.persistence;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DocumentIndexingMapper {

    /* SKIP LOCKED 避免多实例重复消费；必须在同一事务内创建任务并回写。 */
    @Select("SELECT id, payload FROM outbox_events "
            + "WHERE event_type = 'DOCUMENT_VERSION_PUBLISHED' AND processed_at IS NULL "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<Map<String, Object>> claimOutboxEvents(@Param("limit") int limit);

    /* 唯一键 (document_id, version_id) 保证重复事件不重复建任务。 */
    @Insert("INSERT INTO document_index_jobs (organization_id, document_id, version_id, status, attempts, next_attempt_at, created_at, updated_at) "
            + "VALUES (#{organizationId}, #{documentId}, #{versionId}, 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)) "
            + "ON DUPLICATE KEY UPDATE id = id")
    int insertIndexJob(@Param("organizationId") long organizationId,
            @Param("documentId") long documentId, @Param("versionId") long versionId);

    @Update("<script>UPDATE outbox_events SET processed_at = UTC_TIMESTAMP(6) WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int markOutboxProcessed(@Param("ids") List<Long> ids);

    /* PENDING/FAILED 到期可领取；INDEXING 租约过期视为持有者失联，允许重新领取。 */
    @Select("SELECT id, organization_id, document_id, version_id, attempts FROM document_index_jobs "
            + "WHERE ((status IN ('PENDING', 'FAILED') AND next_attempt_at <= UTC_TIMESTAMP(6)) "
            + "OR (status = 'INDEXING' AND updated_at < TIMESTAMPADD(SECOND, -#{leaseSeconds}, UTC_TIMESTAMP(6)))) "
            + "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")
    Map<String, Object> claimIndexJobRow(@Param("leaseSeconds") long leaseSeconds);

    @Update("UPDATE document_index_jobs SET status = 'INDEXING', next_attempt_at = TIMESTAMPADD(SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6)), updated_at = UTC_TIMESTAMP(6) "
            + "WHERE id = #{jobId}")
    int markIndexing(@Param("jobId") long jobId, @Param("leaseSeconds") long leaseSeconds);

    /* 显式按任务行 join 文档与版本，并要求公司一致以防御跨公司数据错配。 */
    @Select("SELECT d.id AS document_id, d.organization_id AS organization_id, d.organization_id AS organization_id, d.work_item_id AS work_item_id, "
            + "d.type AS document_type, d.title AS title, d.status AS document_status, d.visibility AS visibility, d.deleted_at AS deleted_at, "
            + "v.id AS version_id, v.plain_text AS plain_text, v.content_hash AS content_hash "
            + "FROM document_index_jobs j "
            + "JOIN documents d ON d.id = j.document_id AND d.organization_id = j.organization_id "
            + "JOIN document_versions v ON v.id = j.version_id AND v.document_id = j.document_id AND v.organization_id = j.organization_id "
            + "WHERE j.id = #{jobId}")
    Map<String, Object> findIndexingFactRow(@Param("jobId") long jobId);

    @Update("UPDATE document_index_jobs SET status = 'SUCCEEDED', chunk_count = #{chunkCount}, indexed_at = UTC_TIMESTAMP(6), "
            + "error_code = NULL, error_message = NULL, updated_at = UTC_TIMESTAMP(6) "
            + "WHERE id = #{jobId} AND status = 'INDEXING'")
    int markSucceeded(@Param("jobId") long jobId, @Param("chunkCount") int chunkCount);

    /* attempts 先自增再判断上限；MySQL 单表 UPDATE 的 SET 从左到右求值，status 里的 attempts 已是自增后的新值。 */
    @Update("UPDATE document_index_jobs SET attempts = attempts + 1, "
            + "status = IF(attempts >= #{maxAttempts}, 'DEAD', 'FAILED'), "
            + "next_attempt_at = TIMESTAMPADD(MICROSECOND, #{backoffMicros}, UTC_TIMESTAMP(6)), "
            + "error_code = #{errorCode}, error_message = #{errorMessage}, updated_at = UTC_TIMESTAMP(6) "
            + "WHERE id = #{jobId} AND status = 'INDEXING'")
    int markFailed(@Param("jobId") long jobId, @Param("maxAttempts") int maxAttempts,
            @Param("backoffMicros") long backoffMicros, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);
}
