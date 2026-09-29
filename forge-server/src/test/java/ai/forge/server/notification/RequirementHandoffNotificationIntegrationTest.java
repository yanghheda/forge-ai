package ai.forge.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.qa.application.QaService;
import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestCaseType;
import ai.forge.server.qa.domain.TestResultStatus;
import ai.forge.server.workitem.application.OrganizationRequirementService;
import ai.forge.server.workitem.application.OrganizationRequirementStore;
import ai.forge.server.workitem.application.RequirementTransitionService;
import ai.forge.server.workitem.application.RequirementTransitionStore;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import ai.forge.server.workitem.domain.WorkflowAction;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RequirementHandoffNotificationIntegrationTest extends InfrastructureIntegrationTestBase {
    /* 主公司标识，所有需求流转交接通知用例在此公司范围内验证。 */
    private static final long ORG = 8900L;
    /* 产品负责人，同时是需求的报告人与创建人。 */
    private static final long PRODUCT_LEADER = 8901L;
    /* UX 负责人。 */
    private static final long UX_LEADER = 8902L;
    /* 开发负责人。 */
    private static final long DEV_LEADER = 8903L;
    /* 测试负责人。 */
    private static final long QA_LEADER = 8904L;
    /* UX 执行人。 */
    private static final long UX_EXECUTOR = 8905L;
    /* 开发执行人。 */
    private static final long DEV_EXECUTOR = 8906L;
    /* 测试执行人。 */
    private static final long QA_EXECUTOR = 8907L;
    /* 产品执行人。 */
    private static final long PRODUCT_EXECUTOR = 8908L;
    /* 第二位产品负责人，用于让退回操作者与需求报告人不同。 */
    private static final long SECOND_PRODUCT_LEADER = 8909L;
    /* 第二位开发执行人，验证 QA 回流同时通知负责人与执行人。 */
    private static final long SECOND_DEV_EXECUTOR = 8910L;
    /* 第二位测试执行人。 */
    private static final long SECOND_QA_EXECUTOR = 8911L;
    /* 第二位 UX 执行人，验证 UX 退回通知领域全体成员。 */
    private static final long SECOND_UX_EXECUTOR = 8912L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkItemCommandService commands;
    @Autowired private WorkItemStore items;
    @Autowired private RequirementTransitionService transitions;
    @Autowired private OrganizationRequirementService people;
    @Autowired private QaService qa;

    /* 已建立协作人与执行人的主需求标识。 */
    private long requirementId;

    @BeforeEach
    void seedOrganizationAndRequirement() {
        for (long id = 8901L; id <= 8912L; id++) {
            jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    id, "handoff" + id + "@example.com", "handoff" + id + "@example.com", "Member " + id,
                    "$2a$10$placeholder");
        }
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES(?,'Handoff','handoff',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ORG, PRODUCT_LEADER);
        jdbc.update("INSERT INTO organization_item_sequences(organization_id,next_value,version) VALUES(?,1,0)", ORG);
        jdbc.update("UPDATE instance_settings SET default_organization_id=?,initialized_at=UTC_TIMESTAMP(6) WHERE id=1",
                ORG);
        String[] roles = {"PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN", "UX", "DEVELOPER", "QA",
                "PRODUCT", "PRODUCT_ADMIN", "DEVELOPER", "QA", "UX"};
        for (int i = 0; i < roles.length; i++) {
            long user = 8901L + i;
            jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) "
                    + "VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", user, ORG, user);
            jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) "
                    + "SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=? AND organization_id IS NULL", user,
                    roles[i]);
        }
        /* CI 非必需，使研发侧的 SUBMIT_FOR_QA 只依赖已完成的 Dev Task。 */
        jdbc.update("INSERT INTO organization_policies(organization_id,allow_skip_ux,ci_required,updated_by,updated_at,"
                + "version) VALUES(?,FALSE,FALSE,?,UTC_TIMESTAMP(6),0)", ORG, DEV_LEADER);
        requirementId = commands.create(PRODUCT_LEADER, ORG, WorkItemType.REQUIREMENT, "交接需求", "交接需求说明",
                WorkItemPriority.MEDIUM, null, null).id();
        people.replaceParticipants(PRODUCT_LEADER, requirementId, List.of(
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.PRODUCT,
                        PRODUCT_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.UX, UX_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.DEVELOPER,
                        DEV_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.QA, QA_LEADER)));
        people.replaceExecutors(PRODUCT_LEADER, requirementId, RequirementParticipantRole.PRODUCT,
                List.of(PRODUCT_EXECUTOR));
        people.replaceExecutors(UX_LEADER, requirementId, RequirementParticipantRole.UX,
                List.of(UX_EXECUTOR, SECOND_UX_EXECUTOR));
        people.replaceExecutors(DEV_LEADER, requirementId, RequirementParticipantRole.DEVELOPER,
                List.of(DEV_EXECUTOR, SECOND_DEV_EXECUTOR));
        people.replaceExecutors(QA_LEADER, requirementId, RequirementParticipantRole.QA,
                List.of(QA_EXECUTOR, SECOND_QA_EXECUTOR));
        jdbc.update("INSERT INTO documents(organization_id,work_item_id,type,title,status,visibility,created_by,"
                + "created_at,updated_at,version) VALUES(?,?,'PRD','Review','PUBLISHED','ORGANIZATION',?,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)", ORG, requirementId, PRODUCT_LEADER);
    }

    /* a) 产品执行人提交产品评审 → 产品负责人收到 REVIEW_REQUESTED，操作者不收到。 */
    @Test
    void submitProductReviewNotifiesProductLeaderOnly() {
        move(PRODUCT_EXECUTOR, requirementId, WorkflowAction.SUBMIT_PRODUCT_REVIEW);

        Map<String, Object> row = only(rows(PRODUCT_LEADER, "REVIEW_REQUESTED"));
        assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
        assertThat(row.get("title")).asString().startsWith("待评审：");
        assertThat(rows(PRODUCT_EXECUTOR)).isEmpty();
    }

    /* b) 发起四方评审 → 除操作者外的其余三位领域负责人各收到一条 REVIEW_REQUESTED。 */
    @Test
    void startProductReviewNotifiesOtherThreeLeaders() {
        move(PRODUCT_EXECUTOR, requirementId, WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        move(PRODUCT_LEADER, requirementId, WorkflowAction.APPROVE_REQUIREMENT);
        move(PRODUCT_LEADER, requirementId, WorkflowAction.START_PRODUCT_REVIEW);

        for (long leader : List.of(UX_LEADER, DEV_LEADER, QA_LEADER)) {
            Map<String, Object> row = only(rows(leader, "REVIEW_REQUESTED"));
            assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT");
            assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
        }
        /* 操作者（产品负责人）只收到此前提交评审那一条，不因自己发起的会签再收到通知。 */
        assertThat(rows(PRODUCT_LEADER, "REVIEW_REQUESTED")).hasSize(1);
    }

    /* c) 未集齐四方票的 APPROVE_PRODUCT_REVIEW 状态未推进 → 不新增任何通知。 */
    @Test
    void approveProductReviewWithoutFullQuorumDoesNotNotify() {
        move(PRODUCT_EXECUTOR, requirementId, WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        move(PRODUCT_LEADER, requirementId, WorkflowAction.APPROVE_REQUIREMENT);
        move(PRODUCT_LEADER, requirementId, WorkflowAction.START_PRODUCT_REVIEW);
        long before = countInOrg();

        RequirementTransitionStore.TransitionResult result =
                move(PRODUCT_LEADER, requirementId, WorkflowAction.APPROVE_PRODUCT_REVIEW);

        assertThat(result.status()).isEqualTo(WorkItemStatus.PRODUCT_REVIEW);
        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.PRODUCT_REVIEW);
        assertThat(countInOrg()).isEqualTo(before);
        assertThat(rows(UX_LEADER, "REQUIREMENT_HANDOFF")).isEmpty();
    }

    /* d) 集齐四方票并推进到 READY_FOR_UX → UX 负责人收到 REQUIREMENT_HANDOFF。 */
    @Test
    void fullQuorumApproveProductReviewNotifiesUxLeader() {
        move(PRODUCT_EXECUTOR, requirementId, WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        move(PRODUCT_LEADER, requirementId, WorkflowAction.APPROVE_REQUIREMENT);
        move(PRODUCT_LEADER, requirementId, WorkflowAction.START_PRODUCT_REVIEW);

        /* UX 负责人投非最后一票，由产品负责人投出第四票完成会签，避免推进动作的操作者被排除。 */
        move(UX_LEADER, requirementId, WorkflowAction.APPROVE_PRODUCT_REVIEW);
        move(DEV_LEADER, requirementId, WorkflowAction.APPROVE_PRODUCT_REVIEW);
        move(QA_LEADER, requirementId, WorkflowAction.APPROVE_PRODUCT_REVIEW);
        move(PRODUCT_LEADER, requirementId, WorkflowAction.APPROVE_PRODUCT_REVIEW);

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.READY_FOR_UX);
        Map<String, Object> row = only(rows(UX_LEADER, "REQUIREMENT_HANDOFF"));
        assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
    }

    /* e) QA_FAIL → 开发负责人与开发执行人收到 REQUIREMENT_DEVELOPMENT，QA 操作者不收到。 */
    @Test
    void qaFailNotifiesDevelopmentMembersAndExcludesActor() {
        jdbc.update("UPDATE work_items SET status='IN_QA' WHERE id=? AND organization_id=?", requirementId, ORG);
        completeAllTestCases();

        move(QA_EXECUTOR, requirementId, WorkflowAction.QA_FAIL);

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.IN_FIX_VERIFICATION);
        for (long recipient : List.of(DEV_LEADER, DEV_EXECUTOR, SECOND_DEV_EXECUTOR)) {
            Map<String, Object> row = only(rows(recipient, "REQUIREMENT_HANDOFF"));
            assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT_DEVELOPMENT");
            assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
            assertThat(row.get("title")).asString().contains("QA 未通过");
        }
        assertThat(rows(QA_EXECUTOR)).isEmpty();
    }

    /* f) REJECT_REQUIREMENT → 需求报告人收到通知，退回操作者不收到。 */
    @Test
    void rejectRequirementNotifiesReporterOtherThanActor() {
        /* 报告人（创建人）与产品负责人解耦，使退回操作者选第二位产品负责人。 */
        people.replaceParticipants(PRODUCT_LEADER, requirementId, List.of(
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.PRODUCT,
                        SECOND_PRODUCT_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.UX, UX_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.DEVELOPER,
                        DEV_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.QA, QA_LEADER)));
        /* 报告人提交审核，第二位产品负责人退回。 */
        move(PRODUCT_LEADER, requirementId, WorkflowAction.SUBMIT_PRODUCT_REVIEW);
        move(SECOND_PRODUCT_LEADER, requirementId, WorkflowAction.REJECT_REQUIREMENT);

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.DRAFT);
        Map<String, Object> row = only(rows(PRODUCT_LEADER, "REQUIREMENT_HANDOFF"));
        assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
        assertThat(row.get("title")).asString().contains("被退回");
        assertThat(rows(SECOND_PRODUCT_LEADER, "REQUIREMENT_HANDOFF")).isEmpty();
    }

    /* g) 同一 idempotencyKey 重放 → 直接返回既有结果，不新增通知。 */
    @Test
    void idempotentReplayDoesNotWriteAdditionalNotification() {
        String key = UUID.randomUUID().toString();
        transitions.transition(PRODUCT_EXECUTOR, ORG, requirementId, WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                current(requirementId).version(), key, "Review comment");
        assertThat(rows(PRODUCT_LEADER, "REVIEW_REQUESTED")).hasSize(1);

        transitions.transition(PRODUCT_EXECUTOR, ORG, requirementId, WorkflowAction.SUBMIT_PRODUCT_REVIEW,
                current(requirementId).version(), key, "Review comment");

        assertThat(rows(PRODUCT_LEADER, "REVIEW_REQUESTED")).hasSize(1);
    }

    /* h-1) SUBMIT_UX_REVIEW → UX 负责人收到评审通知；APPROVE_UX_REVIEW → 开发负责人收到交接通知。 */
    @Test
    void uxReviewFlowNotifiesUxAndDevelopmentLeaders() {
        jdbc.update("UPDATE work_items SET status='READY_FOR_UX' WHERE id=? AND organization_id=?", requirementId, ORG);
        WorkItem uxTask = commands.createUxTask(UX_LEADER, ORG, requirementId, "Design", "", null, UX_EXECUTOR);
        move(UX_LEADER, requirementId, WorkflowAction.START_UX);
        move(UX_EXECUTOR, uxTask.id(), WorkflowAction.START);
        move(UX_EXECUTOR, uxTask.id(), WorkflowAction.SUBMIT_REVIEW);
        move(UX_LEADER, uxTask.id(), WorkflowAction.APPROVE);
        insertPublishedUxSpec();

        move(UX_EXECUTOR, requirementId, WorkflowAction.SUBMIT_UX_REVIEW,
                List.of("userFlow", "pageList", "keyInteraction", "exceptionState"));

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.UX_REVIEW);
        Map<String, Object> reviewRow = only(rows(UX_LEADER, "REVIEW_REQUESTED"));
        assertThat(reviewRow.get("resource_type")).isEqualTo("REQUIREMENT");
        assertThat(reviewRow.get("resource_id")).isEqualTo(Long.toString(requirementId));
        assertThat(rows(UX_EXECUTOR, "REVIEW_REQUESTED")).isEmpty();

        move(UX_LEADER, requirementId, WorkflowAction.APPROVE_UX_REVIEW);

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.READY_FOR_DEV);
        Map<String, Object> devRow = only(rows(DEV_LEADER, "REQUIREMENT_HANDOFF"));
        assertThat(devRow.get("resource_type")).isEqualTo("REQUIREMENT");
        assertThat(devRow.get("resource_id")).isEqualTo(Long.toString(requirementId));
    }

    /* h-2) REJECT_UX_REVIEW → UX 领域成员（负责人 ∪ 执行人，排除操作者）收到通知。 */
    @Test
    void rejectUxReviewNotifiesUxMembersExcludingActor() {
        jdbc.update("UPDATE work_items SET status='UX_REVIEW' WHERE id=? AND organization_id=?", requirementId, ORG);

        move(UX_LEADER, requirementId, WorkflowAction.REJECT_UX_REVIEW);

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.UX_IN_PROGRESS);
        for (long member : List.of(UX_EXECUTOR, SECOND_UX_EXECUTOR)) {
            Map<String, Object> row = only(rows(member, "REQUIREMENT_HANDOFF"));
            assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT_UX");
            assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
        }
        assertThat(rows(UX_LEADER, "REQUIREMENT_HANDOFF")).isEmpty();
    }

    /* h-3) SUBMIT_FOR_QA → QA 负责人；QA_PASS → 产品领域成员；ACCEPT_REQUIREMENT → 开发负责人（发布页）。 */
    @Test
    void developmentToReleaseChainNotifiesEachNextOwner() {
        jdbc.update("UPDATE work_items SET status='READY_FOR_DEV' WHERE id=? AND organization_id=?", requirementId, ORG);
        completeDevTask();
        move(DEV_EXECUTOR, requirementId, WorkflowAction.SUBMIT_FOR_QA);

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.READY_FOR_QA);
        Map<String, Object> qaRow = only(rows(QA_LEADER, "REQUIREMENT_HANDOFF"));
        assertThat(qaRow.get("resource_type")).isEqualTo("REQUIREMENT");

        WorkItem qaTask = commands.createTask(QA_LEADER, ORG, requirementId, WorkItemType.QA_TASK, "Verify", "", null,
                QA_EXECUTOR);
        move(QA_LEADER, requirementId, WorkflowAction.START_QA);
        move(QA_EXECUTOR, qaTask.id(), WorkflowAction.START);
        move(QA_EXECUTOR, qaTask.id(), WorkflowAction.SUBMIT_REVIEW);
        move(QA_LEADER, qaTask.id(), WorkflowAction.APPROVE);
        completeAllTestCases();
        move(QA_EXECUTOR, requirementId, WorkflowAction.QA_PASS);

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.IN_ACCEPTANCE);
        for (long member : List.of(PRODUCT_LEADER, PRODUCT_EXECUTOR)) {
            assertThat(rows(member, "REQUIREMENT_HANDOFF")).anyMatch(row ->
                    "REQUIREMENT".equals(row.get("resource_type"))
                            && Long.toString(requirementId).equals(row.get("resource_id")));
        }

        move(PRODUCT_EXECUTOR, requirementId, WorkflowAction.ACCEPT_REQUIREMENT);

        assertThat(current(requirementId).status()).isEqualTo(WorkItemStatus.READY_FOR_RELEASE);
        Map<String, Object> releaseRow = rows(DEV_LEADER, "REQUIREMENT_HANDOFF").stream()
                .filter(row -> "REQUIREMENT_RELEASE".equals(row.get("resource_type"))).findFirst().orElseThrow();
        assertThat(releaseRow.get("resource_id")).isEqualTo(Long.toString(requirementId));
    }

    /* 创建并完成一个开发任务，满足 SUBMIT_FOR_QA 对全部 Dev Task 完成的要求。 */
    private void completeDevTask() {
        WorkItem devTask = commands.createTask(DEV_LEADER, ORG, requirementId, WorkItemType.DEV_TASK, "Implement", "",
                null, DEV_EXECUTOR);
        move(DEV_LEADER, requirementId, WorkflowAction.START_DEVELOPMENT);
        move(DEV_EXECUTOR, devTask.id(), WorkflowAction.START);
        move(DEV_EXECUTOR, devTask.id(), WorkflowAction.SUBMIT_REVIEW);
        move(DEV_LEADER, devTask.id(), WorkflowAction.APPROVE);
    }

    /* 创建测试用例、执行全部用例并完成运行，满足 allCasesExecuted 与 QA 放行条件。 */
    private void completeAllTestCases() {
        qa.createCase(QA_EXECUTOR, ORG, requirementId, "Case", "", List.of("step"), "pass",
                TestCasePriority.P1, TestCaseType.FUNCTIONAL);
        var run = qa.createRun(QA_EXECUTOR, ORG, requirementId, "test");
        for (var result : run.results()) {
            qa.updateResult(QA_EXECUTOR, ORG, run.id(), result.id(), TestResultStatus.PASS, "pass", List.of(), 0);
        }
        qa.completeRun(QA_EXECUTOR, ORG, run.id(), qa.run(QA_EXECUTOR, ORG, run.id()).version());
    }

    private void insertPublishedUxSpec() {
        jdbc.update("INSERT INTO documents(organization_id,work_item_id,type,title,status,visibility,created_by,"
                + "created_at,updated_at,version) VALUES(?,?,'UX_SPEC','Spec','PUBLISHED','ORGANIZATION',?,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)", ORG, requirementId, UX_LEADER);
    }

    private WorkItem current(long id) {
        return items.findByIdAndScope(ORG, id).orElseThrow();
    }

    private RequirementTransitionStore.TransitionResult move(long user, long id, WorkflowAction action) {
        return transitions.transition(user, ORG, id, action, current(id).version(),
                UUID.randomUUID().toString(), "Review comment");
    }

    private RequirementTransitionStore.TransitionResult move(
            long user, long id, WorkflowAction action, List<String> checklist) {
        return transitions.transition(user, ORG, id, action, current(id).version(),
                UUID.randomUUID().toString(), "Review comment", checklist);
    }

    private List<Map<String, Object>> rows(long userId) {
        return jdbc.queryForList("SELECT * FROM notifications WHERE organization_id=? AND user_id=? ORDER BY id", ORG,
                userId);
    }

    private List<Map<String, Object>> rows(long userId, String type) {
        return jdbc.queryForList("SELECT * FROM notifications WHERE organization_id=? AND user_id=? AND type=? "
                + "ORDER BY id", ORG, userId, type);
    }

    private Map<String, Object> only(List<Map<String, Object>> rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private long countInOrg() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE organization_id=?", Long.class, ORG);
    }
}
