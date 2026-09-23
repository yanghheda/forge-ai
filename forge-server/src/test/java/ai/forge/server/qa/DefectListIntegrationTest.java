package ai.forge.server.qa;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.qa.application.BugStore;
import ai.forge.server.qa.application.BugView;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DefectListIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 直接搭建公司、用户、需求与缺陷事实，验证公司范围缺陷读取 SQL。 */
    @Autowired
    private JdbcTemplate jdbc;

    /* 被测缺陷读取契约。 */
    @Autowired
    private BugStore bugs;

    @BeforeEach
    void seedDefects() {
        // 公司 3913：创建人 3911、修复人 3912，两个需求各一条有效缺陷，另有软删除缺陷；公司 3923 为对照公司。
        jdbc.update("INSERT INTO users (id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) VALUES "
                + "(3911,'defect-reporter@example.com','defect-reporter@example.com','Reporter One','$2a$10$placeholder','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),"
                + "(3912,'defect-fixer@example.com','defect-fixer@example.com','Fixer One','$2a$10$placeholder','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),"
                + "(3921,'other-reporter@example.com','other-reporter@example.com','Other Reporter','$2a$10$placeholder','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),"
                + "(3922,'other-fixer@example.com','other-fixer@example.com','Other Fixer','$2a$10$placeholder','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organizations (id,name,slug,owner_user_id,created_at,updated_at) VALUES "
                + "(3913,'Defect Org','defect-org',3911,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),"
                + "(3923,'Other Org','other-org',3921,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organization_item_sequences (organization_id,next_value,version) VALUES (3913,10,0),(3923,10,0)");
        jdbc.update("INSERT INTO work_items (id,organization_id,item_number,item_key,type,title,description,"
                + "status,priority,reporter_user_id,assignee_user_id,severity,created_at,updated_at,deleted_at,version) VALUES "
                + "(3914,3913,1,'DEF-1','REQUIREMENT','Checkout','','IN_QA','HIGH',3911,NULL,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),NULL,0),"
                + "(3915,3913,2,'DEF-2','REQUIREMENT','Search','','IN_QA','HIGH',3911,NULL,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),NULL,0),"
                + "(3916,3913,3,'DEF-3','BUG','Checkout fails','','TODO','HIGH',3911,3912,'MAJOR',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),NULL,0),"
                + "(3917,3913,4,'DEF-4','BUG','Search fails','','IN_PROGRESS','HIGH',3911,3912,'MINOR',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),NULL,0),"
                + "(3918,3913,5,'DEF-5','BUG','Deleted bug','','TODO','HIGH',3911,NULL,'MAJOR',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0),"
                + "(3924,3923,1,'OTH-1','REQUIREMENT','Other requirement','','IN_QA','HIGH',3921,NULL,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),NULL,0),"
                + "(3925,3923,2,'OTH-2','BUG','Other bug','','TODO','HIGH',3921,3922,'MAJOR',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),NULL,0)");
        jdbc.update("INSERT INTO bug_details (work_item_id,organization_id,requirement_id,reproduction_steps_json,"
                + "expected_result,actual_result,version) VALUES "
                + "(3916,3913,3914,CAST('[\"open checkout\"]' AS JSON),'Order succeeds','HTTP 500',0),"
                + "(3917,3913,3915,CAST('[\"search\"]' AS JSON),'Results shown','HTTP 500',0),"
                + "(3918,3913,3914,CAST('[\"open\"]' AS JSON),'ok','fail',0),"
                + "(3925,3923,3924,CAST('[\"open\"]' AS JSON),'ok','fail',0)");
    }

    @Test
    void listsCompanyWideDefectsAcrossRequirementsOrderedByNumberDescending() {
        List<BugView> defects = bugs.findAll(3913);

        assertThat(defects).extracting(BugView::id).containsExactly(3917L, 3916L);
        assertThat(defects).extracting(BugView::requirementId).containsExactly(3915L, 3914L);
        assertThat(defects).extracting(BugView::status)
                .containsExactly(WorkItemStatus.IN_PROGRESS, WorkItemStatus.TODO);
        assertThat(defects).allSatisfy(defect -> {
            assertThat(defect.reporterName()).isEqualTo("Reporter One");
            assertThat(defect.createdAt()).isNotNull();
            assertThat(defect.updatedAt()).isNotNull();
        });
    }

    @Test
    void listsOnlyRequestedRequirementDefectsWithReporterAndTimeFields() {
        List<BugView> defects = bugs.findByRequirement(3913, 3914);

        assertThat(defects).extracting(BugView::id).containsExactly(3916L);
        assertThat(defects.get(0).reporterUserId()).isEqualTo(3911L);
        assertThat(defects.get(0).reporterName()).isEqualTo("Reporter One");
        assertThat(defects.get(0).createdAt()).isNotNull();
        assertThat(defects.get(0).updatedAt()).isNotNull();
    }

    @Test
    void excludesOtherOrganizationDefects() {
        assertThat(bugs.findAll(3913)).extracting(BugView::id).doesNotContain(3925L);
        assertThat(bugs.findAll(3923)).extracting(BugView::id).containsExactly(3925L);
    }

    @Test
    void excludesSoftDeletedDefects() {
        assertThat(bugs.findAll(3913)).extracting(BugView::id).doesNotContain(3918L);
        assertThat(bugs.findByRequirement(3913, 3914)).extracting(BugView::id).doesNotContain(3918L);
    }
}
