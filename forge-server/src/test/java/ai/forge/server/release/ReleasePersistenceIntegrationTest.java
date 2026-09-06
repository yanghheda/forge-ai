package ai.forge.server.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.release.application.PrecheckSnapshot;
import ai.forge.server.release.application.ReleaseStore;
import ai.forge.server.release.application.ReleaseView;
import ai.forge.server.release.domain.PrecheckFacts;
import ai.forge.server.release.domain.PrecheckRuleRegistry;
import java.util.List;
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

    private final PrecheckRuleRegistry rules = new PrecheckRuleRegistry();

    @BeforeEach
    void seedScope() {
        jdbc.update("DELETE FROM release_prechecks WHERE workspace_id=3003");
        jdbc.update("DELETE FROM release_items WHERE workspace_id=3003");
        jdbc.update("DELETE FROM releases WHERE workspace_id=3003");
        jdbc.update("DELETE FROM test_runs WHERE workspace_id=3003");
        jdbc.update("DELETE FROM work_items WHERE workspace_id=3003");
        jdbc.update("DELETE FROM project_policies WHERE workspace_id=3003");
        jdbc.update("DELETE FROM project_members WHERE workspace_id=3003");
        jdbc.update("DELETE FROM projects WHERE workspace_id=3003");
        jdbc.update("DELETE FROM workspaces WHERE id=3003");
        jdbc.update("DELETE FROM organizations WHERE id=3002");
        jdbc.update("DELETE FROM users WHERE id=3001");
        jdbc.update("INSERT INTO users (id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                + "VALUES (3001,'release@example.com','release@example.com','Release User','$2a$10$placeholder',"
                + "'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organizations (id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES (3002,'Release Org','release-org',3001,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO workspaces (id,organization_id,name,slug,status,settings_json,created_at,updated_at) "
                + "VALUES (3003,3002,'Release WS','release-ws','ACTIVE',JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO projects (id,workspace_id,`key`,name,status,created_by,created_at,updated_at) "
                + "VALUES (3004,3003,'REL','Release Project','ACTIVE',3001,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO project_policies (project_id,workspace_id,allow_skip_ux,ci_required,updated_by,updated_at,version) "
                + "VALUES (3004,3003,FALSE,TRUE,3001,UTC_TIMESTAMP(6),2)");
        jdbc.update("INSERT INTO work_items (id,workspace_id,project_id,item_number,item_key,type,title,description,"
                + "status,priority,reporter_user_id,created_at,updated_at,version) VALUES "
                + "(3005,3003,3004,1,'REL-1','REQUIREMENT','Release scope','','READY_FOR_RELEASE','HIGH',3001,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),4)");
    }

    @Test
    void versionIsUniqueAndRepeatedPrechecksAppendImmutableSnapshots() {
        ReleaseView release = releases.create(3003, 3004, 3001, "v1.0.0", "production", List.of(3005L), 60);
        release = releases.updateNote(3003, 3004, release.id(), "# v1.0.0\n\n- REL-1", release.version());

        PrecheckFacts firstFacts = releases.loadFacts(3003, 3004, release.id());
        PrecheckSnapshot first = releases.appendPrecheck(3003, 3004, release.id(), 3001, "USER",
                rules.evaluate(firstFacts), firstFacts);
        PrecheckFacts secondFacts = releases.loadFacts(3003, 3004, release.id());
        PrecheckSnapshot second = releases.appendPrecheck(3003, 3004, release.id(), 3001, "USER",
                rules.evaluate(secondFacts), secondFacts);

        assertThat(second.id()).isGreaterThan(first.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM release_prechecks WHERE release_id=?",
                Integer.class, release.id())).isEqualTo(2);
        assertThatThrownBy(() -> releases.create(
                3003, 3004, 3001, "v1.0.0", "production", List.of(3005L), 60))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void newerQaRunInvalidatesOldPrecheckResourceVersions() {
        ReleaseView release = releases.create(3003, 3004, 3001, "v1.0.1", "production", List.of(3005L), 60);
        release = releases.updateNote(3003, 3004, release.id(), "Release note", release.version());
        PrecheckFacts facts = releases.loadFacts(3003, 3004, release.id());
        releases.appendPrecheck(3003, 3004, release.id(), 3001, "USER", rules.evaluate(facts), facts);
        assertThat(releases.find(3003, 3004, release.id()).orElseThrow().latestPrecheck().current()).isTrue();

        jdbc.update("INSERT INTO test_runs (id,workspace_id,project_id,requirement_id,environment,status,started_by,"
                + "started_at,finished_at,summary_json,version) VALUES (3006,3003,3004,3005,'staging','COMPLETED',"
                + "3001,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),JSON_OBJECT('total',1,'passed',1,'failed',0,'blocked',0),1)");

        assertThat(releases.find(3003, 3004, release.id()).orElseThrow().latestPrecheck().current()).isFalse();
    }
}
