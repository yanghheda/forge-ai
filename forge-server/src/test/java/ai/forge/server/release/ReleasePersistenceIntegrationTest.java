package ai.forge.server.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.release.application.PrecheckSnapshot;
import ai.forge.server.release.application.DeploymentStore;
import ai.forge.server.release.application.DeploymentView;
import ai.forge.server.release.application.ReleaseStore;
import ai.forge.server.release.application.ReleaseView;
import ai.forge.server.release.infrastructure.SimulatedDeploymentWorker;
import ai.forge.server.release.domain.PrecheckFacts;
import ai.forge.server.release.domain.PrecheckRuleRegistry;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class ReleasePersistenceIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReleaseStore releases;

    @Autowired
    private DeploymentStore deployments;

    @Autowired
    private SimulatedDeploymentWorker worker;

    private final PrecheckRuleRegistry rules = new PrecheckRuleRegistry();

    @BeforeEach
    void seedScope() {
        jdbc.update("DELETE FROM deployments WHERE organization_id=3003");
        jdbc.update("DELETE FROM work_item_events WHERE organization_id=3003");
        jdbc.update("DELETE FROM release_prechecks WHERE organization_id=3003");
        jdbc.update("DELETE FROM release_items WHERE organization_id=3003");
        jdbc.update("DELETE FROM releases WHERE organization_id=3003");
        jdbc.update("DELETE FROM test_runs WHERE organization_id=3003");
        jdbc.update("DELETE FROM work_items WHERE organization_id=3003");
        jdbc.update("DELETE FROM organization_policies WHERE organization_id=3003");
        jdbc.update("DELETE FROM organizations WHERE id=3003");
        jdbc.update("DELETE FROM users WHERE id=3001");
        jdbc.update("INSERT INTO users (id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                + "VALUES (3001,'release@example.com','release@example.com','Release User','$2a$10$placeholder',"
                + "'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organizations (id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES (3003,'Release Org','release-org',3001,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organization_policies (organization_id,allow_skip_ux,ci_required,updated_by,updated_at,version) "
                + "VALUES (3003,FALSE,TRUE,3001,UTC_TIMESTAMP(6),2)");
        jdbc.update("INSERT INTO work_items (id,organization_id,item_number,item_key,type,title,description,"
                + "status,priority,reporter_user_id,created_at,updated_at,version) VALUES "
                + "(3005,3003,1,'REL-1','REQUIREMENT','Release scope','','READY_FOR_RELEASE','HIGH',3001,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),4)");
    }

    @Test
    void deploymentRequestIsIdempotentAndDecisionUsesOptimisticLock() {
        ReleaseView release = releases.create(3003, 3001, "v1.0.2", "production", List.of(3005L), 60);
        jdbc.update("INSERT INTO release_prechecks (organization_id,release_id,status,checks_json,checked_at,"
                + "checked_by_type,checked_by_id,resource_versions_json) VALUES (3003,?,'PASS',JSON_ARRAY(),"
                + "UTC_TIMESTAMP(6),'USER',3001,JSON_OBJECT('release',0))", release.id());
        long precheckId = jdbc.queryForObject("SELECT MAX(id) FROM release_prechecks WHERE release_id=?",
                Long.class, release.id());

        DeploymentView first = deployments.request(3003, release.id(), 3001, release.version(), precheckId,
                "a".repeat(64), false, "deploy-once", Instant.now().plusSeconds(3600), "HUMAN_REQUEST", null);
        DeploymentView replay = deployments.request(3003, release.id(), 3001, release.version(), precheckId,
                "a".repeat(64), false, "deploy-once", Instant.now().plusSeconds(3600), "HUMAN_REQUEST", null);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(deployments.decide(3003, first.id(), 3001, "APPROVED", first.version())).isTrue();
        assertThat(deployments.decide(3003, first.id(), 3001, "APPROVED", first.version())).isFalse();
    }

    @Test
    void successfulSimulatedDeploymentCompletesReleaseAndRequirementWithEvents() {
        ReleaseView release = releases.create(3003, 3001, "v1.0.3", "production", List.of(3005L), 60);
        jdbc.update("INSERT INTO release_prechecks (organization_id,release_id,status,checks_json,checked_at,"
                + "checked_by_type,checked_by_id,resource_versions_json) VALUES (3003,?,'PASS',JSON_ARRAY(),"
                + "UTC_TIMESTAMP(6),'USER',3001,JSON_OBJECT('release',0))", release.id());
        long precheckId = jdbc.queryForObject("SELECT MAX(id) FROM release_prechecks WHERE release_id=?",
                Long.class, release.id());
        DeploymentView deployment = deployments.request(3003, release.id(), 3001, release.version(),
                precheckId, "b".repeat(64), false, "agent-approved", Instant.now().plusSeconds(3600),
                "AGENT_TOOL", null);

        worker.execute(deployment);

        assertThat(deployments.find(3003, deployment.id()).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(releases.find(3003, release.id()).orElseThrow().status()).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT status FROM work_items WHERE id=3005", String.class))
                .isEqualTo("DONE");
        assertThat(jdbc.queryForList("SELECT event_type FROM work_item_events WHERE work_item_id=3005 "
                + "ORDER BY id", String.class)).containsExactly("MARK_RELEASED", "CLOSE_REQUIREMENT");
    }

    @Test
    void versionIsUniqueAndRepeatedPrechecksAppendImmutableSnapshots() {
        ReleaseView release = releases.create(3003, 3001, "v1.0.0", "production", List.of(3005L), 60);
        release = releases.updateNote(3003, release.id(), "# v1.0.0\n\n- REL-1", release.version());

        PrecheckFacts firstFacts = releases.loadFacts(3003, release.id());
        PrecheckSnapshot first = releases.appendPrecheck(3003, release.id(), 3001, "USER",
                rules.evaluate(firstFacts), firstFacts);
        PrecheckFacts secondFacts = releases.loadFacts(3003, release.id());
        PrecheckSnapshot second = releases.appendPrecheck(3003, release.id(), 3001, "USER",
                rules.evaluate(secondFacts), secondFacts);

        assertThat(second.id()).isGreaterThan(first.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM release_prechecks WHERE release_id=?",
                Integer.class, release.id())).isEqualTo(2);
        assertThatThrownBy(() -> releases.create(
                3003, 3001, "v1.0.0", "production", List.of(3005L), 60))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void newerQaRunInvalidatesOldPrecheckResourceVersions() {
        ReleaseView release = releases.create(3003, 3001, "v1.0.1", "production", List.of(3005L), 60);
        release = releases.updateNote(3003, release.id(), "Release note", release.version());
        PrecheckFacts facts = releases.loadFacts(3003, release.id());
        releases.appendPrecheck(3003, release.id(), 3001, "USER", rules.evaluate(facts), facts);
        assertThat(releases.find(3003, release.id()).orElseThrow().latestPrecheck().current()).isTrue();

        jdbc.update("INSERT INTO test_runs (id,organization_id,requirement_id,environment,status,started_by,"
                + "started_at,finished_at,summary_json,version) VALUES (3006,3003,3005,'staging','COMPLETED',"
                + "3001,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),JSON_OBJECT('total',1,'passed',1,'failed',0,'blocked',0),1)");

        assertThat(releases.find(3003, release.id()).orElseThrow().latestPrecheck().current()).isFalse();
    }
}
