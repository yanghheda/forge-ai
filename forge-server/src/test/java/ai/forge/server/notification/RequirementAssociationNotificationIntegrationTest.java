package ai.forge.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.workitem.application.OrganizationRequirementService;
import ai.forge.server.workitem.application.OrganizationRequirementStore;
import ai.forge.server.workitem.application.OrganizationRequirementView;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RequirementAssociationNotificationIntegrationTest extends InfrastructureIntegrationTestBase {
    /* 主公司标识，所有需求关联通知用例在此公司范围内验证。 */
    private static final long ORG = 8940L;
    /* 产品负责人，同时是需求的报告人与创建人。 */
    private static final long PRODUCT_LEADER = 8941L;
    /* UX 负责人，负责 UX 领域执行人的关联授权。 */
    private static final long UX_LEADER = 8942L;
    /* 第二位 UX 负责人，用于验证负责人更换。 */
    private static final long SECOND_UX_LEADER = 8948L;
    /* 第一位 UX 执行人。 */
    private static final long UX_EXECUTOR = 8945L;
    /* 第二位 UX 执行人。 */
    private static final long SECOND_UX_EXECUTOR = 8949L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkItemCommandService commands;
    @Autowired private OrganizationRequirementService people;

    /* 已建立产品负责人的主需求标识。 */
    private long requirementId;
    /* 主需求的可读编号，用于校验通知文案。 */
    private String requirementKey;

    @BeforeEach
    void seedOrganizationAndRequirement() {
        for (long id = 8941L; id <= 8950L; id++) {
            jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    id, "assoc" + id + "@example.com", "assoc" + id + "@example.com", "Member " + id,
                    "$2a$10$placeholder");
        }
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES(?,'Assoc','assoc',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ORG, PRODUCT_LEADER);
        jdbc.update("INSERT INTO organization_item_sequences(organization_id,next_value,version) VALUES(?,1,0)", ORG);
        jdbc.update("UPDATE instance_settings SET default_organization_id=?,initialized_at=UTC_TIMESTAMP(6) WHERE id=1", ORG);
        String[] roles = {"PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN", "UX", "DEVELOPER", "QA",
                "UX_ADMIN", "UX", "QA"};
        for (int i = 0; i < roles.length; i++) {
            long user = 8941L + i;
            jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) "
                    + "VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", user, ORG, user);
            jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) "
                    + "SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=? AND organization_id IS NULL", user, roles[i]);
        }
        requirementId = commands.create(PRODUCT_LEADER, ORG, WorkItemType.REQUIREMENT, "关联需求", "关联需求说明",
                WorkItemPriority.MEDIUM, null, null).id();
        requirementKey = jdbc.queryForObject("SELECT item_key FROM work_items WHERE id=?", String.class, requirementId);
        /* 创建人自登记为产品负责人：操作者与收件人相同，不产生通知。 */
        people.replaceParticipants(PRODUCT_LEADER, requirementId, List.of(product(PRODUCT_LEADER)));
    }

    /* a) 新关联 UX 负责人 → 该负责人收到一条 WORK_ITEM_ASSIGNED，操作者不收到。 */
    @Test
    void newUxLeaderAssociationNotifiesLeaderAndExcludesActor() {
        people.replaceParticipants(PRODUCT_LEADER, requirementId, List.of(product(PRODUCT_LEADER), ux(UX_LEADER)));

        Map<String, Object> row = only(assignmentRows(UX_LEADER));
        assertThat(row.get("type")).isEqualTo("WORK_ITEM_ASSIGNED");
        assertThat(row.get("resource_type")).isEqualTo("REQUIREMENT");
        assertThat(row.get("resource_id")).isEqualTo(Long.toString(requirementId));
        assertThat(row.get("title")).isEqualTo("新指派：" + requirementKey + " 关联需求");
        assertThat(row.get("body")).isEqualTo("你被关联为需求 " + requirementKey + " 的UX负责人，请及时处理。");
        /* 操作者本人与未涉及成员不收到通知。 */
        assertThat(assignmentRows(PRODUCT_LEADER)).isEmpty();
        assertThat(assignmentRows(8943L)).isEmpty();
    }

    /* b) 重复保存相同负责人集合 → 未变化项不重复通知。 */
    @Test
    void resavingSameLeadersDoesNotNotifyAgain() {
        List<OrganizationRequirementStore.ParticipantAssignment> leaders =
                List.of(product(PRODUCT_LEADER), ux(UX_LEADER));
        people.replaceParticipants(PRODUCT_LEADER, requirementId, leaders);
        long before = countInOrg();

        people.replaceParticipants(PRODUCT_LEADER, requirementId, leaders);

        assertThat(countInOrg()).isEqualTo(before);
        assertThat(assignmentRows(UX_LEADER)).hasSize(1);
    }

    /* c) 更换某领域负责人 A→B → B 收到一条，A 不新增。 */
    @Test
    void changingLeaderNotifiesOnlyNewLeader() {
        people.replaceParticipants(PRODUCT_LEADER, requirementId, List.of(product(PRODUCT_LEADER), ux(UX_LEADER)));

        people.replaceParticipants(PRODUCT_LEADER, requirementId,
                List.of(product(PRODUCT_LEADER), ux(SECOND_UX_LEADER)));

        assertThat(assignmentRows(SECOND_UX_LEADER)).hasSize(1);
        assertThat(assignmentRows(UX_LEADER)).hasSize(1);
    }

    /* d) 新关联 2 名执行人各一条；重复保存不新增；移除其中一人后不新增。 */
    @Test
    void executorReplacementNotifiesNetNewOnly() {
        people.replaceParticipants(PRODUCT_LEADER, requirementId, List.of(product(PRODUCT_LEADER), ux(UX_LEADER)));
        people.replaceExecutors(UX_LEADER, requirementId, RequirementParticipantRole.UX,
                List.of(UX_EXECUTOR, SECOND_UX_EXECUTOR));

        assertThat(assignmentRows(UX_EXECUTOR)).hasSize(1);
        assertThat(assignmentRows(SECOND_UX_EXECUTOR)).hasSize(1);
        assertThat(assignmentRows(UX_EXECUTOR).get(0).get("body"))
                .isEqualTo("你被关联为需求 " + requirementKey + " 的UX执行人，请及时处理。");

        long before = countInOrg();
        /* 重复保存同一执行人集合 → 不新增。 */
        people.replaceExecutors(UX_LEADER, requirementId, RequirementParticipantRole.UX,
                List.of(UX_EXECUTOR, SECOND_UX_EXECUTOR));
        assertThat(countInOrg()).isEqualTo(before);

        /* 仅移除一位执行人（保留另一位）→ 不新增。 */
        people.replaceExecutors(UX_LEADER, requirementId, RequirementParticipantRole.UX, List.of(SECOND_UX_EXECUTOR));
        assertThat(countInOrg()).isEqualTo(before);
        assertThat(assignmentRows(UX_EXECUTOR)).hasSize(1);
        assertThat(assignmentRows(SECOND_UX_EXECUTOR)).hasSize(1);
    }

    /* e) 创建需求时创建人自动成为产品负责人，属自关联，不产生任何通知。 */
    @Test
    void createRegistersCreatorAsProductLeaderWithoutNotification() {
        OrganizationRequirementView created = people.create(PRODUCT_LEADER, "自关联需求", "说明",
                WorkItemPriority.MEDIUM);

        assertThat(people.participants(PRODUCT_LEADER, created.id()))
                .anyMatch(p -> p.role() == RequirementParticipantRole.PRODUCT && p.userId() == PRODUCT_LEADER);
        assertThat(countInOrg()).isZero();
    }

    private OrganizationRequirementStore.ParticipantAssignment product(long userId) {
        return new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.PRODUCT, userId);
    }

    private OrganizationRequirementStore.ParticipantAssignment ux(long userId) {
        return new OrganizationRequirementStore.ParticipantAssignment(RequirementParticipantRole.UX, userId);
    }

    private List<Map<String, Object>> assignmentRows(long userId) {
        return jdbc.queryForList("SELECT * FROM notifications WHERE organization_id=? AND user_id=? "
                + "AND type='WORK_ITEM_ASSIGNED' ORDER BY id", ORG, userId);
    }

    private Map<String, Object> only(List<Map<String, Object>> rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private long countInOrg() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE organization_id=?", Long.class, ORG);
    }
}
