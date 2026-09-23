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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TaskBoardIntegrationTest extends InfrastructureIntegrationTestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkItemCommandService commands;
    @Autowired private OrganizationRequirementService people;
    @Autowired private ConsoleService console;

    /* 已建立协作人与执行人的主需求标识。 */
    private long requirementId;

    @BeforeEach
    void seedOrganizationAndRequirement() {
        for (int i = 1; i <= 9; i++) {
            jdbc.update(
                    "INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    8800 + i, "board" + i + "@example.com", "board" + i + "@example.com", "Member " + i, "$2a$10$placeholder");
        }
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) VALUES(8800,'Board','board',8801,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO organization_item_sequences(organization_id,next_value,version) VALUES(8800,1,0)");
        jdbc.update("UPDATE instance_settings SET default_organization_id=8800,initialized_at=UTC_TIMESTAMP(6) WHERE id=1");
        String[] roles = {"PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN", "UX", "DEVELOPER", "QA", "PRODUCT", "UX_ADMIN"};
        for (int i = 0; i < roles.length; i++) {
            long user = 8801 + i;
            jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) VALUES(?,8800,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", user, user);
            jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=?", user, roles[i]);
        }
        requirementId = commands.create(8801, 8800, WorkItemType.REQUIREMENT, "Board", "Board requirement", WorkItemPriority.MEDIUM, null, null).id();
        people.replaceParticipants(8801, requirementId, List.of(
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.PRODUCT, 8801),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.UX, 8802),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.DEVELOPER, 8803),
                new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.QA, 8804)));
        /* 8805 作为 UX 执行人参与需求，用于验证按执行人查询需求选项。 */
        people.replaceExecutors(8802, requirementId, RequirementParticipantRole.UX, List.of(8805L));
    }

    @Test
    void boardExposesCardFieldsAndHidesRequirementsAndDeletedItems() {
        long assignedId = insertTask(900, "TB-900", "DEV_TASK", "HIGH", 8805L,
                "2026-01-02 03:04:05.000000", "等待接口联调", null);
        long unassignedId = insertTask(901, "TB-901", "QA_TASK", "LOW", null, null, null, null);
        long deletedId = insertTask(902, "TB-902", "UX_TASK", "MEDIUM", 8806L, null, null, "2026-01-03 00:00:00.000000");

        List<ConsoleModels.BoardItem> board = console.board(8805);

        ConsoleModels.BoardItem assigned = board.stream().filter(b -> b.itemKey().equals("TB-900")).findFirst().orElseThrow();
        assertThat(assigned.priority()).isEqualTo("HIGH");
        assertThat(assigned.dueAt()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(assigned.assigneeName()).isEqualTo("Member 5");
        assertThat(assigned.blockedReason()).isEqualTo("等待接口联调");

        ConsoleModels.BoardItem unassigned = board.stream().filter(b -> b.itemKey().equals("TB-901")).findFirst().orElseThrow();
        assertThat(unassigned.assigneeUserId()).isNull();
        assertThat(unassigned.assigneeName()).isNull();

        assertThat(board).noneMatch(b -> b.type().equals("REQUIREMENT"));
        assertThat(board).noneMatch(b -> b.id() == requirementId);
        assertThat(board).noneMatch(b -> b.id() == deletedId);
        assertThat(board).anyMatch(b -> b.id() == assignedId);
        assertThat(board).anyMatch(b -> b.id() == unassignedId);
    }

    @Test
    void boardRequirementsFiltersByParticipantExecutorAndCompany() {
        long otherRequirementId = commands.create(8801, 8800, WorkItemType.REQUIREMENT, "Unrelated", "No link to 8805", WorkItemPriority.MEDIUM, null, null).id();
        long foreignRequirementId = insertForeignRequirement(8900, "OTHER-1");

        /* 作为 UX 执行人可按执行人过滤出主需求。 */
        assertThat(console.boardRequirements(8805, 8805L)).extracting(ConsoleModels.RequirementOption::id).contains(requirementId);
        /* 作为 UX 执行人过滤不到与自己无关的需求。 */
        assertThat(console.boardRequirements(8805, 8805L)).extracting(ConsoleModels.RequirementOption::id).doesNotContain(otherRequirementId);
        /* 作为协作人同样可过滤出主需求。 */
        assertThat(console.boardRequirements(8802, 8802L)).extracting(ConsoleModels.RequirementOption::id).contains(requirementId);
        /* assigneeUserId 为空时返回公司全部未删除需求。 */
        assertThat(console.boardRequirements(8801, null)).extracting(ConsoleModels.RequirementOption::id).contains(requirementId, otherRequirementId);
        /* 其他公司的需求不出现。 */
        assertThat(console.boardRequirements(8801, null)).extracting(ConsoleModels.RequirementOption::id).doesNotContain(foreignRequirementId);
        /* 传入执行人时仅返回该执行人关联的需求。 */
        assertThat(console.boardRequirements(8801, 8805L)).extracting(ConsoleModels.RequirementOption::id).contains(requirementId).doesNotContain(otherRequirementId);
    }

    private long insertTask(long itemNumber, String itemKey, String type, String priority, Long assigneeUserId,
            String dueAt, String blockedReason, String deletedAt) {
        jdbc.update("INSERT INTO work_items(organization_id,item_number,item_key,type,title,description,status,priority,"
                + "assignee_user_id,reporter_user_id,due_at,blocked_reason,created_at,updated_at,deleted_at,version) "
                + "VALUES(8800,?,?,?,?,'','TODO',?,?,8801,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,0)",
                itemNumber, itemKey, type, "Task " + itemKey, priority, assigneeUserId, dueAt, blockedReason, deletedAt);
        return jdbc.queryForObject("SELECT id FROM work_items WHERE organization_id=8800 AND item_key=?", Long.class, itemKey);
    }

    private long insertForeignRequirement(long organizationId, String itemKey) {
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) VALUES(?,?,'other',8801,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                organizationId, "Other");
        jdbc.update("INSERT INTO work_items(organization_id,item_number,item_key,type,title,description,status,priority,"
                + "reporter_user_id,created_at,updated_at,version) VALUES(?,1,?,'REQUIREMENT','Foreign','','DRAFT','MEDIUM',8801,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                organizationId, itemKey);
        return jdbc.queryForObject("SELECT id FROM work_items WHERE organization_id=? AND item_key=?", Long.class, organizationId, itemKey);
    }
}
