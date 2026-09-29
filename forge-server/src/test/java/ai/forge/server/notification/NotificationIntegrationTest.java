package ai.forge.server.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.notification.application.NotificationDraft;
import ai.forge.server.notification.application.NotificationService;
import ai.forge.server.notification.application.NotificationView;
import ai.forge.server.notification.domain.NotificationTarget;
import ai.forge.server.notification.domain.NotificationType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
class NotificationIntegrationTest extends InfrastructureIntegrationTestBase {
    /* 主组织标识，多数用例在此公司范围内验证通知读写。 */
    private static final long ORG = 8810L;
    /* 第二组织标识，用于验证跨公司隔离。 */
    private static final long OTHER_ORG = 8819L;
    /* 持有 ADMIN（含 approval.decide）的普通有效成员。 */
    private static final long ADMIN = 8816L;
    /* 持有 DEVELOPER_ADMIN（同样含 approval.decide）的有效成员。 */
    private static final long DEV_ADMIN = 8813L;
    /* 持有基础 QA 角色、无 approval.decide 的有效成员。 */
    private static final long QA_USER = 8817L;
    /* 持有基础 UX 角色、无 UX_ADMIN 的领域成员。 */
    private static final long UX_USER = 8815L;
    /* 持有基础 DEVELOPER 角色的有效执行人。 */
    private static final long DEV_EXECUTOR = 8821L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationService service;
    @Autowired private PlatformTransactionManager transactionManager;

    /* 已建立协作人与执行人的主需求标识。 */
    private long requirementId;

    @BeforeEach
    void seedOrganizationAndRequirement() {
        for (long id = 8811L; id <= 8822L; id++) {
            jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    id, "notify" + id + "@example.com", "notify" + id + "@example.com", "Member " + id,
                    "$2a$10$placeholder");
        }
        /* 8820 用户被停用，其成员关系仍为 ACTIVE，用于验证反查同时要求 users.status='ACTIVE'。 */
        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=8820");
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ORG, "Notify", "notify-main", 8811L);
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", OTHER_ORG, "Other", "notify-other", 8811L);
        jdbc.update("UPDATE instance_settings SET default_organization_id=?,initialized_at=UTC_TIMESTAMP(6) WHERE id=1", ORG);

        String[] roleCodes = {"PRODUCT_ADMIN", "UX_ADMIN", "DEVELOPER_ADMIN", "QA_ADMIN", "UX", "ADMIN", "QA",
                "PRODUCT", "ADMIN", "ADMIN", "DEVELOPER"};
        for (int i = 0; i < roleCodes.length; i++) {
            long userId = 8811L + i;
            /* 8819 的成员关系被停用，用于验证反查只返回 organization_members.status='ACTIVE'。 */
            String memberStatus = userId == 8819L ? "DISABLED" : "ACTIVE";
            jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) "
                    + "VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", userId, ORG, userId, memberStatus);
            jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) "
                    + "SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=? AND organization_id IS NULL", userId, roleCodes[i]);
        }
        /* 8822 为第二组织的 ADMIN 成员，用于验证按权限反查不会返回其他公司成员。 */
        jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) "
                + "VALUES(8822,?,8822,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", OTHER_ORG);
        jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) "
                + "SELECT 8822,id,UTC_TIMESTAMP(6) FROM roles WHERE code='ADMIN' AND organization_id IS NULL");

        insertRequirement();
        insertParticipant("PRODUCT", 8811L);
        insertParticipant("UX", UX_USER);
        insertParticipant("DEVELOPER", DEV_ADMIN);
        insertParticipant("QA", 8814L);
        insertExecutor("UX", UX_USER);
        insertExecutor("DEVELOPER", DEV_EXECUTOR);
        /* ADMIN 角色不是 DEVELOPER/DEVELOPER_ADMIN，即使被登记为执行人也不应通过执行人语义。 */
        insertExecutor("DEVELOPER", ADMIN);
    }

    @Test
    void notifyWritesOneRowPerRecipientAndStoresFields() {
        service.notify(new NotificationDraft(ORG, List.of(ADMIN, QA_USER), null, NotificationType.WORK_ITEM_ASSIGNED,
                "新任务", "请处理", NotificationTarget.DEFECT, "42"));

        assertThat(rowsFor(ADMIN)).hasSize(1);
        assertThat(rowsFor(QA_USER)).hasSize(1);
        Map<String, Object> row = rowsFor(ADMIN).get(0);
        assertThat(row.get("type")).isEqualTo("WORK_ITEM_ASSIGNED");
        assertThat(row.get("title")).isEqualTo("新任务");
        assertThat(row.get("body")).isEqualTo("请处理");
        assertThat(row.get("resource_type")).isEqualTo("DEFECT");
        assertThat(row.get("resource_id")).isEqualTo("42");
        assertThat(row.get("read_at")).isNull();
    }

    @Test
    void notifyDeduplicatesRecipientsAndExcludesActor() {
        service.notify(new NotificationDraft(ORG, List.of(ADMIN, QA_USER, ADMIN), QA_USER,
                NotificationType.REVIEW_REQUESTED, "评审", "请确认", NotificationTarget.REQUIREMENT, "1"));

        assertThat(rowsFor(ADMIN)).hasSize(1);
        assertThat(rowsFor(QA_USER)).isEmpty();
    }

    @Test
    void notifySkipsWhenNoEffectiveRecipients() {
        service.notify(new NotificationDraft(ORG, List.of(), null, NotificationType.REVIEW_REQUESTED, "t", "b",
                NotificationTarget.REQUIREMENT, "1"));
        service.notify(new NotificationDraft(ORG, List.of(ADMIN), ADMIN, NotificationType.REVIEW_REQUESTED, "t", "b",
                NotificationTarget.REQUIREMENT, "1"));

        assertThat(countInOrg()).isZero();
    }

    @Test
    void notifyTruncatesTitleAndBodyToColumnWidth() {
        service.notify(new NotificationDraft(ORG, List.of(ADMIN), null, NotificationType.REVIEW_REQUESTED,
                "t".repeat(300), "b".repeat(2500), NotificationTarget.REQUIREMENT, "1"));

        Map<String, Object> row = rowsFor(ADMIN).get(0);
        assertThat((String) row.get("title")).hasSize(255);
        assertThat((String) row.get("body")).hasSize(2000);
    }

    @Test
    void notifyRollsBackWithCallingTransaction() {
        TransactionTemplate requiresNew = requiresNew();
        requiresNew.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                    + "VALUES(8841,?,?,?,'$2a$10$placeholder','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    "notify8841@example.com", "notify8841@example.com", "Rollback Member");
            jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                    + "VALUES(8840,'Rollback','notify-rollback',8841,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        });

        assertThatThrownBy(() -> requiresNew.executeWithoutResult(status -> {
            service.notify(new NotificationDraft(8840L, List.of(8841L), null, NotificationType.REVIEW_REQUESTED,
                    "t", "b", NotificationTarget.REQUIREMENT, "1"));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        long count = requiresNew.execute(status ->
                jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE organization_id=8840", Long.class));
        assertThat(count).isZero();

        requiresNew.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM organization_members WHERE organization_id=8840");
            jdbc.update("DELETE FROM notifications WHERE organization_id=8840");
            jdbc.update("DELETE FROM organizations WHERE id=8840");
            jdbc.update("DELETE FROM users WHERE id=8841");
        });
    }

    @Test
    void listReturnsOnlyOwnNotificationsOrderedByCreatedAtDesc() {
        insertNotification(ORG, ADMIN, "WORK_ITEM_ASSIGNED", "旧", "REQUIREMENT", "1",
                "2026-01-01 00:00:00.000000", null);
        insertNotification(ORG, ADMIN, "REVIEW_REQUESTED", "新", "REQUIREMENT", "2",
                "2026-01-02 00:00:00.000000", null);
        insertNotification(ORG, QA_USER, "APPROVAL_REQUESTED", "他人", "APPROVAL", "3",
                "2026-01-03 00:00:00.000000", null);

        List<NotificationView> list = service.list(ADMIN, 30);

        assertThat(list).extracting(NotificationView::title).containsExactly("新", "旧");
        assertThat(list).extracting(NotificationView::resourceType).containsExactly("REQUIREMENT", "REQUIREMENT");
        assertThat(list.get(0).createdAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(list).extracting(NotificationView::type).doesNotContain("APPROVAL_REQUESTED");
        /* limit 被夹取到合法区间：下界 1、上界 100。 */
        assertThat(service.list(ADMIN, 1)).hasSize(1);
        assertThat(service.list(ADMIN, 1000)).hasSize(2);
    }

    @Test
    void unreadCountsOnlyOwnUnreadNotifications() {
        insertNotification(ORG, ADMIN, "REVIEW_REQUESTED", "未读", "REQUIREMENT", "1",
                "2026-01-01 00:00:00.000000", null);
        insertNotification(ORG, ADMIN, "REVIEW_REQUESTED", "已读", "REQUIREMENT", "2",
                "2026-01-01 00:00:00.000000", "2026-01-02 00:00:00.000000");
        insertNotification(ORG, QA_USER, "REVIEW_REQUESTED", "他人未读", "REQUIREMENT", "3",
                "2026-01-01 00:00:00.000000", null);

        assertThat(service.unread(ADMIN)).isEqualTo(1);
        assertThat(service.unread(QA_USER)).isEqualTo(1);
    }

    @Test
    void markReadIsIdempotentAndKeepsFirstReadAt() {
        long id = insertNotification(ORG, ADMIN, "REVIEW_REQUESTED", "待读", "REQUIREMENT", "1",
                "2026-01-01 00:00:00.000000", null);

        service.markRead(ADMIN, id);
        LocalDateTime first = readAt(id);
        assertThat(first).isNotNull();

        service.markRead(ADMIN, id);
        assertThat(readAt(id)).isEqualTo(first);
        assertThat(service.unread(ADMIN)).isZero();
    }

    @Test
    void markReadRejectsForeignAndMissingNotification() {
        long foreign = insertNotification(ORG, QA_USER, "REVIEW_REQUESTED", "他人", "REQUIREMENT", "1",
                "2026-01-01 00:00:00.000000", null);

        assertThatThrownBy(() -> service.markRead(ADMIN, foreign)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(readAt(foreign)).isNull();
        assertThatThrownBy(() -> service.markRead(ADMIN, 999999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listHidesNotificationsFromOtherOrganization() {
        insertNotification(OTHER_ORG, ADMIN, "REVIEW_REQUESTED", "跨公司", "REQUIREMENT", "1",
                "2026-01-01 00:00:00.000000", null);

        assertThat(service.list(ADMIN, 30)).isEmpty();
    }

    @Test
    void permissionHoldersReturnsOnlyActiveMembersWithPermission() {
        List<Long> holders = service.permissionHolders(ORG, "approval.decide");

        assertThat(holders).containsExactlyInAnyOrder(DEV_ADMIN, ADMIN);
        /* 无权限成员、被停用成员、被停用用户与其他公司成员均不得返回。 */
        assertThat(holders).doesNotContain(QA_USER, 8819L, 8820L, 8822L);
    }

    @Test
    void requirementLeadersMatchLeaderSemantics() {
        assertThat(service.requirementLeaders(ORG, requirementId, "PRODUCT")).containsExactly(8811L);
        assertThat(service.requirementLeaders(ORG, requirementId, "DEVELOPER")).containsExactly(DEV_ADMIN);
        /* UX 参与者为基础 UX 角色，不是 UX_ADMIN，因此不构成领域负责人。 */
        assertThat(service.requirementLeaders(ORG, requirementId, "UX")).isEmpty();
    }

    @Test
    void requirementMembersUnionLeadersAndExecutors() {
        assertThat(service.requirementMembers(ORG, requirementId, "DEVELOPER"))
                .containsExactly(DEV_ADMIN, DEV_EXECUTOR);
        /* ADMIN 角色不满足 DEVELOPER 执行人语义，即使被登记在执行人表也不返回。 */
        assertThat(service.requirementMembers(ORG, requirementId, "DEVELOPER")).doesNotContain(ADMIN);
        assertThat(service.requirementMembers(ORG, requirementId, "UX")).containsExactly(UX_USER);
    }

    private void insertRequirement() {
        jdbc.update("INSERT INTO work_items(organization_id,item_number,item_key,type,title,description,status,priority,"
                + "reporter_user_id,created_at,updated_at,version) "
                + "VALUES(?,1,'N-8700','REQUIREMENT','Requirement 8700','','DRAFT','MEDIUM',8811,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                ORG);
        requirementId = jdbc.queryForObject(
                "SELECT id FROM work_items WHERE organization_id=? AND item_key='N-8700'", Long.class, ORG);
    }

    private void insertParticipant(String roleCode, long userId) {
        jdbc.update("INSERT INTO requirement_participants(organization_id,requirement_id,role_code,user_id,"
                + "assigned_by,created_at,updated_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                ORG, requirementId, roleCode, userId, 8811L);
    }

    private void insertExecutor(String roleCode, long userId) {
        jdbc.update("INSERT INTO requirement_executors(organization_id,requirement_id,role_code,user_id,assigned_by,"
                + "created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))", ORG, requirementId, roleCode, userId, 8811L);
    }

    private long insertNotification(long organizationId, long userId, String type, String title, String resourceType,
            String resourceId, String createdAt, String readAt) {
        jdbc.update("INSERT INTO notifications(organization_id,user_id,type,title,body,resource_type,resource_id,"
                + "read_at,created_at) VALUES(?,?,?,?,'',?,?,?,?)",
                organizationId, userId, type, title, resourceType, resourceId, readAt, createdAt);
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM notifications WHERE organization_id=? AND user_id=?", Long.class,
                organizationId, userId);
    }

    private List<Map<String, Object>> rowsFor(long userId) {
        return jdbc.queryForList("SELECT * FROM notifications WHERE organization_id=? AND user_id=? ORDER BY id", ORG,
                userId);
    }

    private long countInOrg() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE organization_id=?", Long.class, ORG);
    }

    private LocalDateTime readAt(long id) {
        return jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?", LocalDateTime.class, id);
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
