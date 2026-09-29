package ai.forge.server.console;

import static org.assertj.core.api.Assertions.*;

import ai.forge.server.console.application.ConsoleModels;
import ai.forge.server.console.application.ConsoleService;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.workitem.application.OrganizationRequirementService;
import ai.forge.server.workitem.application.OrganizationRequirementStore;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TaskBoardCreationOptionsIntegrationTest extends InfrastructureIntegrationTestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkItemCommandService commands;
    @Autowired private OrganizationRequirementService people;
    @Autowired private ConsoleService console;

    /* 主公司内已建立协作人与执行人的需求标识。 */
    private long requirementId;

    @BeforeEach
    void seedOrganizationAndRequirement() {
        for (int i = 1; i <= 10; i++) {
            jdbc.update(
                    "INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    8800 + i, "options" + i + "@example.com", "options" + i + "@example.com", "Member " + i, "$2a$10$placeholder");
        }
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) VALUES(8800,'Options','options',8801,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organization_item_sequences(organization_id,next_value,version) VALUES(8800,1,0)");
        jdbc.update("UPDATE instance_settings SET default_organization_id=8800,initialized_at=UTC_TIMESTAMP(6) WHERE id=1");
        String[] roles = {"PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN", "UX", "DEVELOPER", "QA"};
        for (int i = 0; i < roles.length; i++) {
            long user = 8801 + i;
            jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) VALUES(?,8800,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", user, user);
            jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=?", user, roles[i]);
        }
        /* 8802 同时保留测试基础角色，用于验证撤销 UX_ADMIN 后创建入口消失而看板仍可读。 */
        jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) SELECT 8802,id,UTC_TIMESTAMP(6) FROM roles WHERE code='QA'");
        requirementId = commands.create(8801, 8800, WorkItemType.REQUIREMENT, "Options", "Options requirement", WorkItemPriority.MEDIUM, null, null).id();
        people.replaceParticipants(8801, requirementId, List.of(
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.PRODUCT, 8801),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.UX, 8802),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.DEVELOPER, 8803),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.QA, 8804)));
        people.replaceExecutors(8802, requirementId, RequirementParticipantRole.UX, List.of(8805L));
        people.replaceExecutors(8803, requirementId, RequirementParticipantRole.DEVELOPER, List.of(8806L));
        people.replaceExecutors(8804, requirementId, RequirementParticipantRole.QA, List.of(8807L));
    }

    @Test
    void leaderSeesReadyRequirementWithItsExecutors() {
        moveRequirementTo("READY_FOR_UX");

        ConsoleModels.TaskCreationOption option = onlyOption(console.taskCreationOptions(8802));

        assertThat(option.requirementId()).isEqualTo(requirementId);
        assertThat(option.requirementItemKey()).startsWith("REQ-");
        assertThat(option.taskType()).isEqualTo(WorkItemType.UX_TASK);
        assertThat(option.executors()).extracting(ConsoleModels.ExecutorOption::userId).containsExactly(8805L);
        assertThat(option.executors()).extracting(ConsoleModels.ExecutorOption::displayName).containsExactly("Member 5");
    }

    @Test
    void otherDomainLeadersStayHiddenAtUxPreparation() {
        moveRequirementTo("READY_FOR_UX");

        /* 需求处于 UX 准备态时，开发与测试负责人不获得创建入口。 */
        assertThat(console.taskCreationOptions(8803)).isEmpty();
        assertThat(console.taskCreationOptions(8804)).isEmpty();
    }

    @Test
    void devLeaderSeesDevPreparationWithItsExecutors() {
        moveRequirementTo("READY_FOR_DEV");

        ConsoleModels.TaskCreationOption option = onlyOption(console.taskCreationOptions(8803));
        assertThat(option.taskType()).isEqualTo(WorkItemType.DEV_TASK);
        assertThat(option.executors()).extracting(ConsoleModels.ExecutorOption::userId).containsExactly(8806L);
        /* UX 负责人在开发准备态不再获得创建入口。 */
        assertThat(console.taskCreationOptions(8802)).isEmpty();
    }

    @Test
    void removingDomainAdminRoleHidesCreationEntry() {
        moveRequirementTo("READY_FOR_UX");

        jdbc.update("DELETE mr FROM member_roles mr JOIN roles r ON r.id=mr.role_id WHERE mr.organization_member_id=8802 AND r.code='UX_ADMIN'");

        assertThat(console.taskCreationOptions(8802)).isEmpty();
    }

    @Test
    void otherCompanyRequirementsStayInvisible() {
        moveRequirementTo("READY_FOR_UX");
        long foreignRequirementId = seedForeignRequirement();

        assertThat(console.taskCreationOptions(8802)).extracting(ConsoleModels.TaskCreationOption::requirementId)
                .contains(requirementId)
                .doesNotContain(foreignRequirementId);
        /* 另一公司的负责人只看到本公司需求。 */
        assertThat(console.taskCreationOptions(8810)).extracting(ConsoleModels.TaskCreationOption::requirementId)
                .containsExactly(foreignRequirementId);
    }

    /* 需求准备态只能由流转规则进入；测试直接改写状态以隔离创建入口的验证目标。 */
    private void moveRequirementTo(String status) {
        jdbc.update("UPDATE work_items SET status=? WHERE organization_id=8800 AND id=?", status, requirementId);
    }

    private ConsoleModels.TaskCreationOption onlyOption(List<ConsoleModels.TaskCreationOption> options) {
        assertThat(options).hasSize(1);
        return options.get(0);
    }

    private long seedForeignRequirement() {
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) VALUES(8900,'Other','other',8810,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO work_items(organization_id,item_number,item_key,type,title,description,status,priority,"
                + "reporter_user_id,created_at,updated_at,version) VALUES(8900,1,'OTHER-1','REQUIREMENT','Foreign','','READY_FOR_UX','MEDIUM',8810,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)");
        long foreignRequirementId = jdbc.queryForObject("SELECT id FROM work_items WHERE organization_id=8900 AND item_key='OTHER-1'", Long.class);
        jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) VALUES(8910,8900,8810,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) SELECT 8910,id,UTC_TIMESTAMP(6) FROM roles WHERE code='UX_ADMIN'");
        jdbc.update("INSERT INTO requirement_participants(organization_id,requirement_id,role_code,user_id,assigned_by,created_at,updated_at) "
                + "VALUES(8900,?,'UX',8810,8810,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", foreignRequirementId);
        return foreignRequirementId;
    }
}
