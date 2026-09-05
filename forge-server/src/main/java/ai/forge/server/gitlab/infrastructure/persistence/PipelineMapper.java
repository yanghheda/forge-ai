package ai.forge.server.gitlab.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PipelineMapper {

    @Select("SELECT r.id repository_id,r.workspace_id,r.project_id,r.connection_id,r.remote_project_id,c.base_url,"
            + "s.type,s.ciphertext,s.iv,s.key_version,s.fingerprint FROM git_repositories r "
            + "JOIN gitlab_connections c ON c.id=r.connection_id AND c.workspace_id=r.workspace_id AND c.status='ACTIVE' "
            + "JOIN secrets s ON s.id=c.credential_secret_id AND s.workspace_id=r.workspace_id "
            + "WHERE r.workspace_id=#{workspaceId} AND r.project_id=#{projectId} AND r.status='ACTIVE'")
    List<Map<String, Object>> findContext(@Param("workspaceId") long workspaceId, @Param("projectId") long projectId);

    @Insert("INSERT INTO pipeline_runs (workspace_id,repository_id,merge_request_id,remote_pipeline_id,ref,commit_sha,"
            + "status,web_url,started_at,finished_at,remote_updated_at,last_synced_at,summary_json) "
            + "VALUES (#{workspaceId},#{repositoryId},#{mergeRequestId},#{remoteId},#{ref},#{sha},#{status},#{webUrl},"
            + "#{startedAt},#{finishedAt},#{remoteUpdatedAt},UTC_TIMESTAMP(6),CAST(#{summaryJson} AS JSON)) "
            + "ON DUPLICATE KEY UPDATE status=VALUES(status),web_url=VALUES(web_url),started_at=VALUES(started_at),"
            + "finished_at=VALUES(finished_at),remote_updated_at=VALUES(remote_updated_at),last_synced_at=UTC_TIMESTAMP(6),"
            + "summary_json=VALUES(summary_json)")
    int upsert(@Param("workspaceId") long workspaceId, @Param("repositoryId") long repositoryId,
            @Param("mergeRequestId") Long mergeRequestId, @Param("remoteId") long remoteId,
            @Param("ref") String ref, @Param("sha") String sha, @Param("status") String status,
            @Param("webUrl") String webUrl, @Param("startedAt") java.time.LocalDateTime startedAt,
            @Param("finishedAt") java.time.LocalDateTime finishedAt,
            @Param("remoteUpdatedAt") java.time.LocalDateTime remoteUpdatedAt,
            @Param("summaryJson") String summaryJson);

    @Select("SELECT id,workspace_id,repository_id,merge_request_id,remote_pipeline_id,ref,commit_sha,status,web_url,"
            + "started_at,finished_at,remote_updated_at,last_synced_at,summary_json FROM pipeline_runs WHERE workspace_id=#{workspaceId} "
            + "AND repository_id=#{repositoryId} AND remote_pipeline_id=#{remoteId}")
    List<Map<String, Object>> findByRemoteId(@Param("workspaceId") long workspaceId,
            @Param("repositoryId") long repositoryId, @Param("remoteId") long remoteId);

    @Select("SELECT p.id,p.workspace_id,p.repository_id,p.merge_request_id,p.remote_pipeline_id,p.ref,p.commit_sha,"
            + "p.status,p.web_url,p.started_at,p.finished_at,p.remote_updated_at,p.last_synced_at,p.summary_json FROM pipeline_runs p "
            + "JOIN git_repositories r ON r.id=p.repository_id AND r.workspace_id=p.workspace_id "
            + "WHERE p.workspace_id=#{workspaceId} AND r.project_id=#{projectId} AND p.id=#{pipelineId}")
    List<Map<String, Object>> find(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("pipelineId") long pipelineId);

    @Select("SELECT p.id,p.workspace_id,p.repository_id,p.merge_request_id,p.remote_pipeline_id,p.ref,p.commit_sha,"
            + "p.status,p.web_url,p.started_at,p.finished_at,p.remote_updated_at,p.last_synced_at,p.summary_json FROM pipeline_runs p "
            + "JOIN git_repositories r ON r.id=p.repository_id AND r.workspace_id=p.workspace_id "
            + "WHERE p.workspace_id=#{workspaceId} AND r.project_id=#{projectId} ORDER BY p.id DESC LIMIT #{limit}")
    List<Map<String, Object>> list(@Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId, @Param("limit") int limit);
}
