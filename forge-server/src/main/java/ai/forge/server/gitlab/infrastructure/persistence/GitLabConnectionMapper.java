package ai.forge.server.gitlab.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GitLabConnectionMapper {

    @Insert("INSERT INTO secrets (workspace_id,type,ciphertext,iv,key_version,fingerprint,created_at,rotated_at) "
            + "VALUES (#{workspaceId},'GITLAB_TOKEN',#{ciphertext},#{iv},#{keyVersion},#{fingerprint},UTC_TIMESTAMP(6),NULL)")
    int insertSecret(
            @Param("workspaceId") long workspaceId,
            @Param("ciphertext") String ciphertext,
            @Param("iv") String iv,
            @Param("keyVersion") int keyVersion,
            @Param("fingerprint") String fingerprint);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Insert("INSERT INTO gitlab_connections "
            + "(workspace_id,name,base_url,credential_secret_id,webhook_secret_id,status,last_tested_at,created_by,created_at,updated_at,version) "
            + "VALUES (#{workspaceId},#{name},#{baseUrl},#{secretId},NULL,'UNVERIFIED',NULL,#{createdBy},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)")
    int insertConnection(
            @Param("workspaceId") long workspaceId,
            @Param("createdBy") long createdBy,
            @Param("name") String name,
            @Param("baseUrl") String baseUrl,
            @Param("secretId") long secretId);

    @Select("SELECT c.id,c.workspace_id,c.name,c.base_url,s.fingerprint,c.status,c.last_tested_at,c.version "
            + "FROM gitlab_connections c JOIN secrets s ON s.id=c.credential_secret_id AND s.workspace_id=c.workspace_id "
            + "WHERE c.workspace_id=#{workspaceId} ORDER BY c.id")
    List<Map<String, Object>> findConnections(@Param("workspaceId") long workspaceId);

    @Select("SELECT c.id,c.workspace_id,c.name,c.base_url,s.fingerprint,c.status,c.last_tested_at,c.version "
            + "FROM gitlab_connections c JOIN secrets s ON s.id=c.credential_secret_id AND s.workspace_id=c.workspace_id "
            + "WHERE c.workspace_id=#{workspaceId} AND c.id=#{connectionId}")
    List<Map<String, Object>> findConnection(
            @Param("workspaceId") long workspaceId, @Param("connectionId") long connectionId);

    @Select("SELECT s.id,s.workspace_id,s.type,s.ciphertext,s.iv,s.key_version,s.fingerprint "
            + "FROM gitlab_connections c JOIN secrets s ON s.id=c.credential_secret_id AND s.workspace_id=c.workspace_id "
            + "WHERE c.workspace_id=#{workspaceId} AND c.id=#{connectionId}")
    List<Map<String, Object>> findCredential(
            @Param("workspaceId") long workspaceId, @Param("connectionId") long connectionId);

    @Update("UPDATE secrets s JOIN gitlab_connections c ON c.credential_secret_id=s.id AND c.workspace_id=s.workspace_id "
            + "SET s.ciphertext=#{ciphertext},s.iv=#{iv},s.key_version=#{keyVersion},s.fingerprint=#{fingerprint},"
            + "s.rotated_at=UTC_TIMESTAMP(6),c.status='UNVERIFIED',c.last_tested_at=NULL,c.updated_at=UTC_TIMESTAMP(6),c.version=c.version+1 "
            + "WHERE c.workspace_id=#{workspaceId} AND c.id=#{connectionId} AND c.version=#{expectedVersion}")
    int rotateCredential(
            @Param("workspaceId") long workspaceId,
            @Param("connectionId") long connectionId,
            @Param("expectedVersion") long expectedVersion,
            @Param("ciphertext") String ciphertext,
            @Param("iv") String iv,
            @Param("keyVersion") int keyVersion,
            @Param("fingerprint") String fingerprint);

    @Update("UPDATE gitlab_connections SET status=#{status},last_tested_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) "
            + "WHERE workspace_id=#{workspaceId} AND id=#{connectionId}")
    int recordTest(
            @Param("workspaceId") long workspaceId,
            @Param("connectionId") long connectionId,
            @Param("status") String status);

    @Insert("INSERT INTO git_repositories "
            + "(workspace_id,project_id,connection_id,remote_project_id,path_with_namespace,http_url,default_branch,status,last_synced_at,created_at,updated_at,version) "
            + "VALUES (#{workspaceId},#{projectId},#{connectionId},#{remoteProjectId},#{pathWithNamespace},#{httpUrl},#{defaultBranch},'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)")
    int insertRepository(
            @Param("workspaceId") long workspaceId,
            @Param("projectId") long projectId,
            @Param("connectionId") long connectionId,
            @Param("remoteProjectId") String remoteProjectId,
            @Param("pathWithNamespace") String pathWithNamespace,
            @Param("httpUrl") String httpUrl,
            @Param("defaultBranch") String defaultBranch);

    @Select("SELECT id,workspace_id,project_id,connection_id,remote_project_id,path_with_namespace,http_url,"
            + "default_branch,status,last_synced_at,version FROM git_repositories "
            + "WHERE workspace_id=#{workspaceId} AND project_id=#{projectId} AND status='ACTIVE'")
    List<Map<String, Object>> findActiveRepository(
            @Param("workspaceId") long workspaceId, @Param("projectId") long projectId);
}
