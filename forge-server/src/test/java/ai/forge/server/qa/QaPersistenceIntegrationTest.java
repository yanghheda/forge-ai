package ai.forge.server.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.qa.application.QaStore;
import ai.forge.server.qa.application.TestCaseView;
import ai.forge.server.qa.application.TestRunView;
import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestResultStatus;
import ai.forge.server.qa.domain.TestRunStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class QaPersistenceIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private QaStore store;

    @BeforeEach
    void seedScope() {
        jdbc.update("DELETE FROM audit_logs WHERE organization_id=2803");
        jdbc.update("DELETE FROM test_results WHERE organization_id=2803");
        jdbc.update("DELETE FROM test_runs WHERE organization_id=2803");
        jdbc.update("DELETE FROM test_cases WHERE organization_id=2803");
        jdbc.update("DELETE FROM work_items WHERE organization_id=2803");
        jdbc.update("DELETE FROM organizations WHERE id=2803");
        jdbc.update("DELETE FROM users WHERE id=2801");
        jdbc.update("INSERT INTO users (id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                + "VALUES (2801,'qa@example.com','qa@example.com','QA','$2a$10$placeholder','ACTIVE',"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organizations (id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES (2803,'QA Org','qa-org',2801,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO work_items (id,organization_id,item_number,item_key,type,title,description,"
                + "status,priority,reporter_user_id,created_at,updated_at,version) VALUES "
                + "(2805,2803,1,'QA-1','REQUIREMENT','Quality','','READY_FOR_QA','HIGH',2801,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)");
    }

    @Test
    void snapshotsCasesFreezesCompletionAndRequiresAuditedReopen() {
        TestCaseView mandatory = createCase("Critical path", TestCasePriority.P0);
        TestCaseView optional = createCase("Minor path", TestCasePriority.P2);
        TestRunView run = store.createRun(2803, 2805, 2801, "staging");

        createCase("Added later", TestCasePriority.P0);
        assertThat(run.results()).hasSize(2);
        var first = run.results().get(0);
        var second = run.results().get(1);
        store.updateResult(2803, run.id(), first.id(), 2801, TestResultStatus.PASS,
                "works", List.of("evidence://first"), first.version());
        store.updateResult(2803, run.id(), second.id(), 2801, TestResultStatus.SKIPPED,
                "low priority", List.of(), second.version());

        TestRunView completed = store.completeRun(2803, run.id(), 2801, 1);
        assertThat(completed.status()).isEqualTo(TestRunStatus.COMPLETED);
        assertThat(completed.summary().passed()).isEqualTo(1);
        assertThat(completed.summary().skipped()).isEqualTo(1);
        assertThat(completed.decision().passed()).isTrue();
        assertThat(completed.results()).extracting(result -> result.testCaseId())
                .containsExactly(mandatory.id(), optional.id());

        assertThatThrownBy(() -> store.updateResult(2803, run.id(), first.id(), 2801,
                TestResultStatus.FAIL, "changed", List.of(), 1)).isInstanceOf(VersionConflictException.class);

        TestRunView reopened = store.reopenRun(2803, run.id(), 2801, completed.version(),
                "Regression evidence changed", "qa-request-1");
        assertThat(reopened.status()).isEqualTo(TestRunStatus.IN_PROGRESS);
        assertThat(reopened.summary()).isNull();
        assertThat(store.findLatestCompletedSummary(2803, 2805)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='QA_RUN_REOPEN' AND resource_id=?",
                Integer.class, run.id())).isEqualTo(1);
    }

    @Test
    void staleResultVersionCannotOverwriteFirstExecution() {
        createCase("Concurrent", TestCasePriority.P1);
        TestRunView run = store.createRun(2803, 2805, 2801, "staging");
        var result = run.results().get(0);

        store.updateResult(2803, run.id(), result.id(), 2801, TestResultStatus.PASS,
                "first", List.of(), 0);

        assertThatThrownBy(() -> store.updateResult(2803, run.id(), result.id(), 2801,
                TestResultStatus.FAIL, "second", List.of(), 0)).isInstanceOf(VersionConflictException.class);
        assertThat(store.findRun(2803, run.id()).orElseThrow().results().get(0).status())
                .isEqualTo(TestResultStatus.PASS);
    }

    private TestCaseView createCase(String title, TestCasePriority priority) {
        return store.createCase(2803, 2805, 2801, title, "", List.of("Execute"), "Expected", priority);
    }
}
