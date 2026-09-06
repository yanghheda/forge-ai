package ai.forge.server.qa;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.qa.application.BugStore;
import ai.forge.server.qa.application.BugView;
import ai.forge.server.qa.domain.BugAction;
import ai.forge.server.qa.domain.BugSeverity;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class BugPersistenceIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkItemStore workItems;

    @Autowired
    private BugStore bugs;

    @BeforeEach
    void seedScope() {
        jdbc.update("DELETE FROM work_item_events WHERE workspace_id=2903");
        jdbc.update("DELETE FROM work_item_relations WHERE workspace_id=2903");
        jdbc.update("DELETE FROM bug_details WHERE workspace_id=2903");
        jdbc.update("DELETE FROM test_results WHERE workspace_id=2903");
        jdbc.update("DELETE FROM test_runs WHERE workspace_id=2903");
        jdbc.update("DELETE FROM test_cases WHERE workspace_id=2903");
        jdbc.update("DELETE FROM work_items WHERE workspace_id=2903");
        jdbc.update("DELETE FROM project_item_sequences WHERE project_id=2904");
        jdbc.update("DELETE FROM projects WHERE workspace_id=2903");
        jdbc.update("DELETE FROM workspaces WHERE id=2903");
        jdbc.update("DELETE FROM organizations WHERE id=2902");
        jdbc.update("DELETE FROM users WHERE id=2901");
        jdbc.update("INSERT INTO users (id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                + "VALUES (2901,'bug@example.com','bug@example.com','Bug QA','$2a$10$placeholder','ACTIVE',"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organizations (id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES (2902,'Bug Org','bug-org',2901,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO workspaces (id,organization_id,name,slug,status,settings_json,created_at,updated_at) "
                + "VALUES (2903,2902,'Bug WS','bug-ws','ACTIVE',JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO projects (id,workspace_id,`key`,name,status,created_by,created_at,updated_at) "
                + "VALUES (2904,2903,'BUG','Bug Project','ACTIVE',2901,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO project_item_sequences (project_id,next_value,version) VALUES (2904,3,0)");
        jdbc.update("INSERT INTO work_items (id,workspace_id,project_id,item_number,item_key,type,title,description,"
                + "status,priority,reporter_user_id,created_at,updated_at,version) VALUES "
                + "(2905,2903,2904,1,'BUG-1','REQUIREMENT','Checkout','','IN_QA','HIGH',2901,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0),"
                + "(2906,2903,2904,2,'BUG-2','DEV_TASK','Fix checkout','','IN_PROGRESS','HIGH',2901,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)");
    }

    @Test
    void createsRelationsThenPersistsFixVerifyAndReopenHistory() {
        WorkItem item = workItems.create(2903, 2904, 2901, WorkItemType.BUG, "Checkout fails", "HTTP 500",
                WorkItemStatus.OPEN, WorkItemPriority.HIGH, null, null);
        BugView created = bugs.create(item, BugSeverity.BLOCKER, 2905, null, null,
                List.of("Open checkout", "Submit order"), "Order succeeds", "HTTP 500", 2906L);

        assertThat(created.status()).isEqualTo(WorkItemStatus.OPEN);
        assertThat(created.severity()).isEqualTo(BugSeverity.BLOCKER);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_item_relations WHERE source_id=?",
                Integer.class, created.id())).isEqualTo(2);

        BugView fixing = bugs.transition(2903, 2904, created.id(), 2901, BugAction.START_FIX,
                "IN_PROGRESS", "", List.of(), 0, "bug-start");
        BugView resolved = bugs.transition(2903, 2904, created.id(), 2901, BugAction.RESOLVE,
                "RESOLVED", "Handle null cart", List.of("mr://42", "commit://abc"), fixing.version(), "bug-resolve");
        BugView verified = bugs.transition(2903, 2904, created.id(), 2901, BugAction.VERIFY,
                "VERIFIED", "Retested", List.of(), resolved.version(), "bug-verify");
        BugView reopened = bugs.transition(2903, 2904, created.id(), 2901, BugAction.REOPEN,
                "OPEN", "Regression reproduced", List.of(), verified.version(), "bug-reopen");

        assertThat(resolved.fixEvidence()).containsExactly("mr://42", "commit://abc");
        assertThat(reopened.status()).isEqualTo(WorkItemStatus.OPEN);
        assertThat(jdbc.queryForObject("SELECT verified_by FROM bug_details WHERE work_item_id=?",
                Long.class, created.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_item_events WHERE work_item_id=?",
                Integer.class, created.id())).isEqualTo(4);
    }
}
