package ai.forge.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.workitem.application.OrganizationRequirementService;
import ai.forge.server.workitem.application.OrganizationRequirementStore;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AssignmentNotificationIntegrationTest extends InfrastructureIntegrationTestBase {
    /* 主公司标识，所有指派通知用例在此公司范围内验证。 */
    private static final long ORG = 8800L;
    /* 产品负责人，同时是需求报告人与创建需求的操作者。 */
    private static final long PRODUCT_LEADER = 8801L;
    /* UX 负责人，负责 UX 任务创建与改派授权。 */
    private static final long UX_LEADER = 8802L;
    /* 开发负责人，负责开发任务创建。 */
    private static final long DEV_LEADER = 8803L;
    /* 测试负责人，负责测试任务与缺陷创建。 */
    private static final long QA_LEADER = 8804L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkItemCommandService commands;
    @Autowired private OrganizationRequirementService people;

    /* 已建立协作人与执行人的主需求标识。 */
    private long requirementId;

    @BeforeEach
    void seedOrganizationAndRequirement() {
        for (long id = 8801L; id <= 8810L; id++) {
            jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    id, "assign" + id + "@example.com", "assign" + id + "@example.com", "Member " + id,
                    "$2a$10$placeholder");
        }
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES(?,'Assign','assign',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ORG, PRODUCT_LEADER);
        jdbc.update("INSERT INTO organization_item_sequences(organization_id,next_value,version) VALUES(?,1,0)", ORG);
        jdbc.update("UPDATE instance_settings SET default_organization_id=?,initialized_at=UTC_TIMESTAMP(6) WHERE id=1", ORG);
        String[] roles = {"PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN", "UX", "DEVELOPER", "QA", "UX",
                "DEVELOPER", "QA"};
        for (int i = 0; i < roles.length; i++) {
            long user = 8801L + i;
            jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) "
                    + "VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", user, ORG, user);
            jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) "
                    + "SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=? AND organization_id IS NULL", user, roles[i]);
        }
        requirementId = commands.create(PRODUCT_LEADER, ORG, WorkItemType.REQUIREMENT, "指派需求", "指派需求说明",
                WorkItemPriority.MEDIUM, null, null).id();
        people.replaceParticipants(PRODUCT_LEADER, requirementId, List.of(
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.PRODUCT, PRODUCT_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.UX, UX_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.DEVELOPER, DEV_LEADER),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.QA, QA_LEADER)));
        /* 每个领域登记两个执行人，覆盖创建指派与改派两种场景。 */
        people.replaceExecutors(UX_LEADER, requirementId, RequirementParticipantRole.UX, List.of(8805L, 8808L));
        people.replaceExecutors(DEV_LEADER, requirementId, RequirementParticipantRole.DEVELOPER, List.of(8806L, 8809L));
        people.replaceExecutors(QA_LEADER, requirementId, RequirementParticipantRole.QA, List.of(8807L, 8810L));
    }

    @Test
    void createRequirementWithAssigneeNotifiesAssigneeAndExcludesActor() {
        long reqId = commands.create(PRODUCT_LEADER, ORG, WorkItemType.REQUIREMENT, "新建需求", "说明",
                WorkItemPriority.MEDIUM, 8805L, null).id();

        assertThat(assignmentRows(8805L)).hasSize(1);
        Map<String, Object> row = assignmentRows(8805L).get(0);
        assertThat(row.get("type")).isEqualTo("WORK_ITEM_ASSIGNED");
        assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(reqId));
        assertThat(row.get("title")).asString().startsWith("新指派：");
        assertThat(row.get("body")).isEqualTo("你是该需求的负责人，请及时处理。");
        /* 操作者本人不收到自己触发的指派通知。 */
        assertThat(assignmentRows(PRODUCT_LEADER)).isEmpty();
    }

    /*
     * 保证「创建需求 + 指派通知」在同一事务内提交或回滚：create 必须与 createTask/update 一样标注 @Transactional，
     * 否则直连入口（WorkItemController、Agent create_requirement）会因工作项写入与通知写入各自开事务而破坏一致性。
     */
    @Test
    void createIsTransactionalSoRequirementAndNotificationCommitTogether() throws Exception {
        Method create = WorkItemCommandService.class.getMethod("create", long.class, long.class, WorkItemType.class,
                String.class, String.class, WorkItemPriority.class, Long.class, Instant.class);

        assertThat(create.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void createUxTaskNotifiesAssigneeWithUxStageTarget() {
        long taskId = createTaskAt("READY_FOR_UX", UX_LEADER, WorkItemType.UX_TASK, 8805L);

        assertThat(taskId).isPositive();
        Map<String, Object> row = onlyAssignmentRow(8805L);
        assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT_UX");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
        assertThat(row.get("body")).isEqualTo("你是该UX 任务的负责人，请及时处理。");
    }

    @Test
    void createDevTaskNotifiesAssigneeWithDevelopmentStageTarget() {
        createTaskAt("READY_FOR_DEV", DEV_LEADER, WorkItemType.DEV_TASK, 8806L);

        Map<String, Object> row = onlyAssignmentRow(8806L);
        assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT_DEVELOPMENT");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
    }

    @Test
    void createQaTaskNotifiesAssigneeWithQaStageTarget() {
        createTaskAt("READY_FOR_QA", QA_LEADER, WorkItemType.QA_TASK, 8807L);

        Map<String, Object> row = onlyAssignmentRow(8807L);
        assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT_QA");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
        assertThat(row.get("body")).isEqualTo("你是该测试任务的负责人，请及时处理。");
    }

    @Test
    void createBugNotifiesAssigneeWithDefectTarget() {
        jdbc.update("UPDATE work_items SET status='IN_QA' WHERE id=?", requirementId);

        long bugId = commands.createTask(8807L, ORG, requirementId, WorkItemType.BUG, "缺陷单", "", null, 8806L).id();

        Map<String, Object> row = onlyAssignmentRow(8806L);
        assertThat(row.get("resource_type")).isEqualTo("DEFECT");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(bugId));
        assertThat(row.get("body")).isEqualTo("你是该缺陷的负责人，请及时处理。");
    }

    @Test
    void updateReassignNotifiesOnlyNewAssignee() {
        updateAssignee(PRODUCT_LEADER, 8805L);
        assertThat(assignmentRows(8805L)).hasSize(1);

        updateAssignee(PRODUCT_LEADER, 8808L);

        assertThat(assignmentRows(8808L)).hasSize(1);
        /* 原负责人不再新增通知。 */
        assertThat(assignmentRows(8805L)).hasSize(1);
    }

    @Test
    void updateSameAssigneeDoesNotNotify() {
        updateAssignee(PRODUCT_LEADER, 8805L);

        updateAssignee(PRODUCT_LEADER, 8805L);

        assertThat(assignmentRows(8805L)).hasSize(1);
    }

    @Test
    void updateSelfAssignmentDoesNotNotify() {
        updateAssignee(PRODUCT_LEADER, 8805L);

        /* 负责人从他人改为操作者本人时，因收件人被排除而不新增。 */
        updateAssignee(PRODUCT_LEADER, PRODUCT_LEADER);

        assertThat(assignmentRows(PRODUCT_LEADER)).isEmpty();
        assertThat(assignmentRows(8805L)).hasSize(1);
    }

    /* 直接把需求改到目标状态，隔离任务创建对阶段准入的验证目标。 */
    private long createTaskAt(String status, long leader, WorkItemType type, long assignee) {
        jdbc.update("UPDATE work_items SET status=? WHERE id=?", status, requirementId);
        return commands.createTask(leader, ORG, requirementId, type, "任务 " + type.name(), "", null, assignee).id();
    }

    /* 以当前乐观锁版本改派需求负责人，走通既有 update 授权与版本校验。 */
    private void updateAssignee(long actor, Long assignee) {
        long version = jdbc.queryForObject("SELECT version FROM work_items WHERE id=?", Long.class, requirementId);
        commands.update(actor, ORG, requirementId, null, null, null, assignee, null, version);
    }

    private List<Map<String, Object>> assignmentRows(long userId) {
        return jdbc.queryForList("SELECT * FROM notifications WHERE organization_id=? AND user_id=? "
                + "AND type='WORK_ITEM_ASSIGNED' ORDER BY id", ORG, userId);
    }

    private Map<String, Object> onlyAssignmentRow(long userId) {
        List<Map<String, Object>> rows = assignmentRows(userId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}
