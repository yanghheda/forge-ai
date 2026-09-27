package ai.forge.server.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.gitlab.application.PipelineStore;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PipelineAssociationIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PipelineStore store;

    @BeforeEach
    void seedDeliveryFacts() {
        for (String table : new String[] {"merge_requests", "branches", "work_items", "git_repositories",
                "gitlab_connections", "secrets"}) {
            jdbc.update("DELETE FROM " + table + " WHERE organization_id = 953");
        }
        jdbc.update("DELETE FROM organizations WHERE id = 953");
        jdbc.update("DELETE FROM users WHERE id = 951");
        jdbc.update("INSERT INTO users (id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                + "VALUES (951,'pipeline@example.com','pipeline@example.com','Pipeline','$2a$10$placeholder','ACTIVE',"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organizations (id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES (953,'Pipeline Org','pipeline-org',951,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO secrets (id,organization_id,type,ciphertext,iv,key_version,fingerprint,created_at) "
                + "VALUES (955,953,'GITLAB_TOKEN','unused','unused',1,'unused',UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO gitlab_connections (id,organization_id,name,base_url,credential_secret_id,status,created_by,created_at,updated_at) "
                + "VALUES (956,953,'Pipeline','https://gitlab.example',955,'ACTIVE',951,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO git_repositories (id,organization_id,connection_id,remote_project_id,path_with_namespace,http_url,default_branch,status,last_synced_at,created_at,updated_at) "
                + "VALUES (957,953,956,'123','org/repo','https://gitlab.example/org/repo','main','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO work_items (id,organization_id,item_number,item_key,type,title,description,status,priority,"
                + "reporter_user_id,created_at,updated_at,version) "
                + "VALUES (958,953,1,'PIPE-1','DEV_TASK','Pipeline task','','IN_PROGRESS','HIGH',951,UTC_TIMESTAMP(6),"
                + "UTC_TIMESTAMP(6),0)");
        jdbc.update("INSERT INTO branches (id,organization_id,repository_id,work_item_id,name,commit_sha,status,"
                + "remote_updated_at,last_synced_at) "
                + "VALUES (959,953,957,958,'feature/pipe-1','abc123','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        mergeRequest(960, 40, "feature/pipe-1", "merged");
        mergeRequest(962, 41, "feature/pipe-closed", "closed");
    }

    @Test
    void associatesPipelineWithMergedMergeRequest() {
        assertThat(store.findMergeRequestId(953L, 957L, 958L, "feature/pipe-1")).contains(960L);
    }

    @Test
    void ignoresMergeRequestOfAnotherBranchOrState() {
        assertThat(store.findMergeRequestId(953L, 957L, 958L, "feature/pipe-closed")).isEmpty();
        assertThat(store.findMergeRequestId(953L, 957L, 958L, "feature/pipe-missing")).isEmpty();
    }

    private void mergeRequest(long id, long iid, String sourceBranch, String state) {
        jdbc.update("INSERT INTO merge_requests (id,organization_id,repository_id,work_item_id,remote_mr_iid,title,"
                        + "source_branch,target_branch,state,web_url,head_sha,remote_updated_at,last_synced_at,version) "
                        + "VALUES (?,953,957,958,?,?,?,'main',?,?,'abc123',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                id, iid, "MR " + iid, sourceBranch, state, "https://gitlab.example/mr/" + iid);
    }
}
