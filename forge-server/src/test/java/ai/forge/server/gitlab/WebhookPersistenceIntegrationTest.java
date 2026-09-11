package ai.forge.server.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.gitlab.application.WebhookChange;
import ai.forge.server.gitlab.application.WebhookDelivery;
import ai.forge.server.gitlab.application.WebhookStore;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WebhookPersistenceIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WebhookStore store;

    @BeforeEach
    void seedScope() {
        jdbc.update("INSERT INTO users (id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                + "VALUES (901,'hook@example.com','hook@example.com','Hook','$2a$10$placeholder','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organizations (id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES (903,'Hook Org','hook-org',901,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO secrets (id,organization_id,type,ciphertext,iv,key_version,fingerprint,created_at) "
                + "VALUES (905,903,'GITLAB_TOKEN','unused','unused',1,'unused',UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO gitlab_connections (id,organization_id,name,base_url,credential_secret_id,status,created_by,created_at,updated_at) "
                + "VALUES (906,903,'Hook','https://gitlab.example',905,'ACTIVE',901,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO git_repositories (id,organization_id,connection_id,remote_project_id,path_with_namespace,http_url,default_branch,status,last_synced_at,created_at,updated_at) "
                + "VALUES (907,903,906,'123','org/repo','https://gitlab.example/org/repo','main','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
    }

    @Test
    void olderAndEquivalentDeliveriesDoNotOverwriteSnapshotOrDuplicateOutbox() {
        process(908L, "delivery-new", "success", LocalDateTime.of(2026, 9, 6, 10, 0));
        process(909L, "delivery-old", "failed", LocalDateTime.of(2026, 9, 6, 9, 0));
        process(910L, "delivery-equal", "canceled", LocalDateTime.of(2026, 9, 6, 10, 0));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM pipeline_runs WHERE repository_id=907 AND remote_pipeline_id=81", String.class))
                .isEqualTo("success");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type='PIPELINE_CHANGED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT status FROM webhook_deliveries WHERE id IN (908,909,910) ORDER BY id", String.class))
                .containsExactly("PROCESSED", "PROCESSED", "PROCESSED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM webhook_deliveries WHERE id IN (908,909,910) AND payload IS NOT NULL",
                Integer.class))
                .isZero();
    }

    private void process(long deliveryId, String deliveryKey, String status, LocalDateTime updatedAt) {
        jdbc.update("INSERT INTO webhook_deliveries (id,organization_id,connection_id,delivery_key,event_type,payload_hash,"
                        + "payload,status,attempts,next_attempt_at,received_at) VALUES (?,?,?,?,?,'hash',JSON_OBJECT(),"
                        + "'PROCESSING',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                deliveryId, 903L, 906L, deliveryKey, "Pipeline Hook");
        WebhookDelivery delivery = new WebhookDelivery(deliveryId, 903L, 906L, deliveryKey,
                "Pipeline Hook", "hash", "{}", 0, LocalDateTime.now());
        WebhookChange change = new WebhookChange.PipelineChanged(
                "123", 81L, "main", "abc", status, "https://gitlab.example/pipelines/81",
                updatedAt.minusMinutes(1), updatedAt, updatedAt);
        store.process(delivery, change);
    }
}
