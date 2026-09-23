package ai.forge.server.workitem;

import static org.assertj.core.api.Assertions.*;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.application.*;
import ai.forge.server.workitem.domain.*;
import ai.forge.server.qa.application.*;
import ai.forge.server.qa.domain.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DeliveryWorkflowIntegrationTest extends InfrastructureIntegrationTestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkItemCommandService commands;
    @Autowired private WorkItemStore items;
    @Autowired private RequirementTransitionService transitions;
    @Autowired private OrganizationRequirementService people;
    @Autowired private BugService bugs;
    @Autowired private QaService qa;
    @Autowired private ai.forge.server.console.application.ConsoleService console;
    private long requirementId;

    @BeforeEach
    void seedIndependentLeadsAndExecutors() {
        for (int i = 1; i <= 9; i++) {
            jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    8800+i, "delivery"+i+"@example.com", "delivery"+i+"@example.com", "Member "+i, "$2a$10$placeholder");
        }
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) VALUES(8800,'Delivery','delivery',8801,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organization_item_sequences(organization_id,next_value,version) VALUES(8800,1,0)");
        jdbc.update("UPDATE instance_settings SET default_organization_id=8800,initialized_at=UTC_TIMESTAMP(6) WHERE id=1");
        String[] roles = {"PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN", "UX", "DEVELOPER", "QA", "PRODUCT", "UX_ADMIN"};
        for (int i=0; i<roles.length; i++) {
            long user=8801+i;
            jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) VALUES(?,8800,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", user, user);
            jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=?", user, roles[i]);
        }
        requirementId=commands.create(8801,8800,WorkItemType.REQUIREMENT,"Delivery","Complete workflow",WorkItemPriority.MEDIUM,null,null).id();
        people.replaceParticipants(8801, requirementId, List.of(
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.PRODUCT,8801),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.UX,8802),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.DEVELOPER,8803),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.QA,8804)));
        people.replaceExecutors(8801,requirementId,RequirementParticipantRole.PRODUCT,List.of(8808L));
        people.replaceExecutors(8802,requirementId,RequirementParticipantRole.UX,List.of(8805L));
        people.replaceExecutors(8803,requirementId,RequirementParticipantRole.DEVELOPER,List.of(8806L));
        people.replaceExecutors(8804,requirementId,RequirementParticipantRole.QA,List.of(8807L));
        jdbc.update("INSERT INTO documents(organization_id,work_item_id,type,title,status,visibility,created_by,created_at,updated_at,version) VALUES(8800,?,'PRD','Review','PUBLISHED','ORGANIZATION',8801,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",requirementId);
    }

    @Test
    void reviewRequiresFourIndependentVotesAndNeverCreatesUxTasksAutomatically() {
        move(8808,requirementId,WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        move(8801,requirementId,WorkflowAction.APPROVE_REQUIREMENT);
        move(8801,requirementId,WorkflowAction.START_PRODUCT_REVIEW);
        assertThatThrownBy(() -> move(8809,requirementId,WorkflowAction.APPROVE_PRODUCT_REVIEW)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> people.replaceExecutors(8802,requirementId,RequirementParticipantRole.UX,List.of(8805L))).isInstanceOf(InvalidTransitionException.class);
        for (long lead : List.of(8801L,8802L,8803L)) {
            move(lead,requirementId,WorkflowAction.APPROVE_PRODUCT_REVIEW);
            assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.PRODUCT_REVIEW);
        }
        move(8804,requirementId,WorkflowAction.APPROVE_PRODUCT_REVIEW);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.READY_FOR_UX);
        assertThat(commands.tasks(8801,8800,requirementId)).isEmpty();
        assertThatThrownBy(() -> move(8802,requirementId,WorkflowAction.START_UX)).isInstanceOf(WorkflowGuardFailedException.class);
        assertThatThrownBy(() -> commands.createTask(8809,8800,requirementId,WorkItemType.UX_TASK,"Wrong lead","",null,8805L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> commands.createTask(8802,8800,requirementId,WorkItemType.UX_TASK,"Wrong executor","",null,8806L)).isInstanceOf(ResourceNotFoundException.class);
        WorkItem task=commands.createUxTask(8802,8800,requirementId,"Design","",null,8805L);
        move(8802,requirementId,WorkflowAction.START_UX);
        move(8805,task.id(),WorkflowAction.START);
        move(8805,task.id(),WorkflowAction.BLOCK);
        move(8805,task.id(),WorkflowAction.UNBLOCK);
        move(8805,task.id(),WorkflowAction.SUBMIT_REVIEW);
        assertThatThrownBy(() -> move(8805,task.id(),WorkflowAction.APPROVE)).isInstanceOf(ResourceNotFoundException.class);
        move(8802,task.id(),WorkflowAction.REJECT);
        assertThat(current(task.id()).status()).isEqualTo(WorkItemStatus.IN_PROGRESS);
        move(8805,task.id(),WorkflowAction.SUBMIT_REVIEW);
        move(8802,task.id(),WorkflowAction.APPROVE);
        assertThat(current(task.id()).status()).isEqualTo(WorkItemStatus.DONE);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.UX_IN_PROGRESS);
    }

    /* 产品域执行人与产品负责人一样可以发起四方评审；无该需求产品职责的产品角色仍被拒绝。 */
    @Test
    void productExecutorCanStartReviewWhileUnassignedProductRoleIsRejected() {
        jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) VALUES(8810,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                8810+"@example.com", 8810+"@example.com", "Member 10", "$2a$10$placeholder");
        jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) VALUES(8810,8800,8810,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) SELECT 8810,id,UTC_TIMESTAMP(6) FROM roles WHERE code='PRODUCT'");
        move(8808,requirementId,WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        move(8801,requirementId,WorkflowAction.APPROVE_REQUIREMENT);
        assertThatThrownBy(() -> move(8810,requirementId,WorkflowAction.START_PRODUCT_REVIEW)).isInstanceOf(ResourceNotFoundException.class);
        move(8808,requirementId,WorkflowAction.START_PRODUCT_REVIEW);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.PRODUCT_REVIEW);
    }

    @Test
    void missingExecutorBlocksReviewAndRejectionInvalidatesAllVotes() {
        people.replaceExecutors(8802,requirementId,RequirementParticipantRole.UX,List.of());
        move(8808,requirementId,WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        assertThatThrownBy(() -> move(8801,requirementId,WorkflowAction.APPROVE_REQUIREMENT)).isInstanceOf(WorkflowGuardFailedException.class);
        people.replaceExecutors(8802,requirementId,RequirementParticipantRole.UX,List.of(8805L));
        move(8801,requirementId,WorkflowAction.APPROVE_REQUIREMENT);
        move(8801,requirementId,WorkflowAction.START_PRODUCT_REVIEW);
        move(8801,requirementId,WorkflowAction.APPROVE_PRODUCT_REVIEW);
        move(8802,requirementId,WorkflowAction.REJECT_PRODUCT_REVIEW);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.DRAFT);
        move(8808,requirementId,WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        move(8801,requirementId,WorkflowAction.APPROVE_REQUIREMENT);
        move(8801,requirementId,WorkflowAction.START_PRODUCT_REVIEW);
        assertThat(people.participants(8801,requirementId)).allMatch(p -> !p.reviewApproved());
    }

    @Test
    void bugsCanBeFixedDuringTestingButUnexecutedCasesBlockFixVerification() {
        jdbc.update("UPDATE work_items SET status='READY_FOR_QA' WHERE id=? AND organization_id=8800",requirementId);
        WorkItem task=commands.createTask(8804,8800,requirementId,WorkItemType.QA_TASK,"Execute regression","",null,8807L);
        move(8804,requirementId,WorkflowAction.START_QA);
        var first=qa.createCase(8807,8800,requirementId,"First","",List.of("run"),"pass",TestCasePriority.P1,TestCaseType.FUNCTIONAL);
        qa.createCase(8807,8800,requirementId,"Second","",List.of("run"),"pass",TestCasePriority.P1,TestCaseType.FUNCTIONAL);
        var run=qa.createRun(8807,8800,requirementId,"test");
        var result=run.results().stream().filter(r -> r.testCaseId()==first.id()).findFirst().orElseThrow();
        qa.updateResult(8807,8800,run.id(),result.id(),TestResultStatus.FAIL,"failed",List.of(),0);
        var bug=bugs.create(8807,8800,requirementId,run.id(),result.id(),null,"Failure",BugSeverity.MAJOR,List.of("run"),"pass","failed",8806L);
        assertThat(bug.status()).isEqualTo(WorkItemStatus.TODO);
        assertThat(console.board(8806)).anyMatch(b -> b.id()==bug.id() && b.assigneeUserId()==8806L && b.lane().equals("TODO"));
        assertThatThrownBy(() -> move(8804,requirementId,WorkflowAction.QA_FAIL)).isInstanceOf(WorkflowGuardFailedException.class);
        var fixing=bugs.transition(8806,8800,bug.id(),BugAction.START_FIX,0,null,List.of(),"fix-start");
        var fixed=bugs.transition(8806,8800,bug.id(),BugAction.RESOLVE,fixing.version(),"Fixed",List.of("commit://abc"),"fix-submit");
        assertThatThrownBy(() -> bugs.transition(8806,8800,bug.id(),BugAction.VERIFY,fixed.version(),null,List.of(),"self-verify")).isInstanceOf(ResourceNotFoundException.class);
        var verified=bugs.transition(8807,8800,bug.id(),BugAction.VERIFY,fixed.version(),null,List.of(),"qa-verify");
        assertThat(verified.status()).isEqualTo(WorkItemStatus.DONE);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.IN_QA);
        var second=run.results().stream().filter(r -> r.id()!=result.id()).findFirst().orElseThrow();
        qa.updateResult(8807,8800,run.id(),second.id(),TestResultStatus.PASS,"pass",List.of(),0);
        run=qa.completeRun(8807,8800,run.id(),qa.run(8807,8800,run.id()).version());
        move(8804,requirementId,WorkflowAction.QA_FAIL);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.IN_FIX_VERIFICATION);
        assertThatThrownBy(() -> move(8804,requirementId,WorkflowAction.QA_PASS)).isInstanceOf(WorkflowGuardFailedException.class);
        run=qa.reopenRun(8807,8800,run.id(),run.version(),"Verify fixed case","retest");
        qa.updateResult(8807,8800,run.id(),result.id(),TestResultStatus.PASS,"verified",List.of(),1);
        qa.completeRun(8807,8800,run.id(),qa.run(8807,8800,run.id()).version());
        move(8807,task.id(),WorkflowAction.START);
        move(8807,task.id(),WorkflowAction.SUBMIT_REVIEW);
        move(8804,task.id(),WorkflowAction.APPROVE);
        move(8804,requirementId,WorkflowAction.QA_PASS);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.IN_ACCEPTANCE);
        move(8801,requirementId,WorkflowAction.ACCEPT_REQUIREMENT);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.READY_FOR_RELEASE);
    }

    private WorkItem current(long id) { return items.findByIdAndScope(8800,id).orElseThrow(); }
    private void move(long user, long id, WorkflowAction action) {
        transitions.transition(user,8800,id,action,current(id).version(),UUID.randomUUID().toString(),"Review comment");
    }
}
