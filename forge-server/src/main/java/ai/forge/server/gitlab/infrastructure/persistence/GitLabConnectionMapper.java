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

    @Insert("INSERT INTO secrets (organization_id,type,ciphertext,iv,key_version,fingerprint,created_at,rotated_at) "
            + "VALUES (#{organizationId},'GITLAB_TOKEN',#{ciphertext},#{iv},#{keyVersion},#{fingerprint},UTC_TIMESTAMP(6),NULL)")
    int insertSecret(
            @Param("organizationId") long organizationId,
            @Param("ciphertext") String ciphertext,
            @Param("iv") String iv,
            @Param("keyVersion") int keyVersion,
            @Param("fingerprint") String fingerprint);

    @Insert("INSERT INTO secrets (organization_id,type,ciphertext,iv,key_version,fingerprint,created_at,rotated_at) "
            + "VALUES (#{organizationId},'GITLAB_WEBHOOK_SECRET',#{ciphertext},#{iv},#{keyVersion},#{fingerprint},"
            + "UTC_TIMESTAMP(6),NULL)")
    int insertWebhookSecret(@Param("organizationId") long organizationId,
            @Param("ciphertext") String ciphertext, @Param("iv") String iv,
            @Param("keyVersion") int keyVersion, @Param("fingerprint") String fingerprint);

    @Update("UPDATE gitlab_connections SET webhook_secret_id=#{secretId},updated_at=UTC_TIMESTAMP(6),version=version+1 "
            + "WHERE organization_id=#{organizationId} AND id=#{connectionId}")
    int attachWebhookSecret(@Param("organizationId") long organizationId,
            @Param("connectionId") long connectionId, @Param("secretId") long secretId);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Insert("INSERT INTO gitlab_connections "
            + "(organization_id,name,base_url,credential_secret_id,webhook_secret_id,status,last_tested_at,created_by,created_at,updated_at,version) "
            + "VALUES (#{organizationId},#{name},#{baseUrl},#{secretId},NULL,'UNVERIFIED',NULL,#{createdBy},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)")
    int insertConnection(
            @Param("organizationId") long organizationId,
            @Param("createdBy") long createdBy,
            @Param("name") String name,
            @Param("baseUrl") String baseUrl,
            @Param("secretId") long secretId);

    @Select("SELECT c.id,c.organization_id,c.name,c.base_url,s.fingerprint,c.status,c.last_tested_at,c.version "
            + "FROM gitlab_connections c JOIN secrets s ON s.id=c.credential_secret_id AND s.organization_id=c.organization_id "
            + "WHERE c.organization_id=#{organizationId} ORDER BY c.id")
    List<Map<String, Object>> findConnections(@Param("organizationId") long organizationId);

    @Select("SELECT c.id,c.organization_id,c.name,c.base_url,s.fingerprint,c.status,c.last_tested_at,c.version "
            + "FROM gitlab_connections c JOIN secrets s ON s.id=c.credential_secret_id AND s.organization_id=c.organization_id "
            + "WHERE c.organization_id=#{organizationId} AND c.id=#{connectionId}")
    List<Map<String, Object>> findConnection(
            @Param("organizationId") long organizationId, @Param("connectionId") long connectionId);

    @Select("SELECT s.id,s.organization_id,s.type,s.ciphertext,s.iv,s.key_version,s.fingerprint "
            + "FROM gitlab_connections c JOIN secrets s ON s.id=c.credential_secret_id AND s.organization_id=c.organization_id "
            + "WHERE c.organization_id=#{organizationId} AND c.id=#{connectionId}")
    List<Map<String, Object>> findCredential(
            @Param("organizationId") long organizationId, @Param("connectionId") long connectionId);

    @Update("UPDATE secrets s JOIN gitlab_connections c ON c.credential_secret_id=s.id AND c.organization_id=s.organization_id "
            + "SET s.ciphertext=#{ciphertext},s.iv=#{iv},s.key_version=#{keyVersion},s.fingerprint=#{fingerprint},"
            + "s.rotated_at=UTC_TIMESTAMP(6),c.status='UNVERIFIED',c.last_tested_at=NULL,c.updated_at=UTC_TIMESTAMP(6),c.version=c.version+1 "
            + "WHERE c.organization_id=#{organizationId} AND c.id=#{connectionId} AND c.version=#{expectedVersion}")
    int rotateCredential(
            @Param("organizationId") long organizationId,
            @Param("connectionId") long connectionId,
            @Param("expectedVersion") long expectedVersion,
            @Param("ciphertext") String ciphertext,
            @Param("iv") String iv,
            @Param("keyVersion") int keyVersion,
            @Param("fingerprint") String fingerprint);

    @Update("UPDATE gitlab_connections SET status=#{status},last_tested_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) "
            + "WHERE organization_id=#{organizationId} AND id=#{connectionId}")
    int recordTest(
            @Param("organizationId") long organizationId,
            @Param("connectionId") long connectionId,
            @Param("status") String status);

    @Insert("INSERT INTO git_repositories "
            + "(organization_id,connection_id,remote_project_id,path_with_namespace,http_url,default_branch,status,last_synced_at,created_at,updated_at,version) "
            + "VALUES (#{organizationId},#{connectionId},#{remoteProjectId},#{pathWithNamespace},#{httpUrl},#{defaultBranch},'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)")
    int insertRepository(
            @Param("organizationId") long organizationId,
            @Param("connectionId") long connectionId,
            @Param("remoteProjectId") String remoteProjectId,
            @Param("pathWithNamespace") String pathWithNamespace,
            @Param("httpUrl") String httpUrl,
            @Param("defaultBranch") String defaultBranch);

    @Select("SELECT id,organization_id,connection_id,remote_project_id,path_with_namespace,http_url,"
            + "default_branch,status,last_synced_at,version FROM git_repositories "
            + "WHERE organization_id = #{organizationId} AND status='ACTIVE'")
    List<Map<String, Object>> findActiveRepository(
            @Param("organizationId") long organizationId);
}
