package ai.forge.server.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.agent.application.ApprovalService;
import ai.forge.server.agent.application.PendingApprovalView;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/* 验证 GET /api/v1/approvals 待审批列表的应用服务语义：公司范围、排除本人、升序、隐藏冻结材料与非法状态。 */
@Transactional
class PendingApprovalIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 本测试专用公司标识，与其他集成测试夹具隔离。 */
    private static final long ORG = 8960L;
    /* 第二组织标识，用于验证跨公司隔离。 */
    private static final long OTHER_ORG = 8969L;
    /* 持有 ADMIN 角色（含 approval.decide）的有效审批人。 */
    private static final long APPROVER = 8961L;
    /* 持 PRODUCT 角色、不持有 approval.decide 的发起人甲。 */
    private static final long REQUESTER_A = 8962L;
    /* 持 PRODUCT 角色、不持有 approval.decide 的发起人乙。 */
    private static final long REQUESTER_B = 8963L;
    /* 持 QA 角色、不持有 approval.decide 的普通成员。 */
    private static final long PLAIN = 8964L;

    /* 搭建身份事实并直接断言待审批查询结果。 */
    @Autowired private JdbcTemplate jdbc;
    /* 直接调用审批应用服务验证列表读取语义。 */
    @Autowired private ApprovalService approvalService;

    /* 造数时生成唯一 Run 与审批标识的单调序号。 */
    private long sequence;

    @BeforeEach
    void seedOrganizationAndMembers() {
        for (long id = APPROVER; id <= PLAIN; id++) {
            long current = id;
            jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,"
                    + "created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    current, "pending" + current + "@example.com", "pending" + current + "@example.com",
                    displayName(current), "$2a$10$placeholder");
        }
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ORG, "Pending", "pending-org", APPROVER);
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", OTHER_ORG, "Pending Other", "pending-other",
                APPROVER);
        insertMember(APPROVER, ORG, "ADMIN");
        insertMember(REQUESTER_A, ORG, "PRODUCT");
        insertMember(REQUESTER_B, ORG, "PRODUCT");
        insertMember(PLAIN, ORG, "QA");
    }

    /* a) 不持有 approval.decide 的用户被拒绝，且不返回任何审批内容。 */
    @Test
    void userWithoutDecidePermissionIsRejected() {
        insertApproval(ORG, REQUESTER_A, "rotate_secret", "HIGH", "替换生产密钥", "PENDING",
                "2026-03-01 00:00:00.000000");

        assertThatThrownBy(() -> approvalService.pending(PLAIN, ORG, "PENDING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /* b) 持权用户只看到本公司、非本人发起的 PENDING，且按 expires_at 升序并带发起人显示名。 */
    @Test
    void pendingReturnsOnlyOthersPendingInExpiryOrderWithRequesterName() {
        String later = insertApproval(ORG, REQUESTER_A, "rotate_secret", "HIGH", "较晚失效",
                "PENDING", "2026-03-02 00:00:00.000000");
        String sooner = insertApproval(ORG, REQUESTER_B, "deploy_release", "HIGH", "较早失效",
                "PENDING", "2026-03-01 00:00:00.000000");
        /* 审批人自己发起的 PENDING 必须被排除（HIGH 不可自批）。 */
        insertApproval(ORG, APPROVER, "rotate_secret", "HIGH", "本人发起", "PENDING",
                "2026-02-01 00:00:00.000000");

        List<PendingApprovalView> views = approvalService.pending(APPROVER, ORG, "PENDING");

        assertThat(views).extracting(PendingApprovalView::id).containsExactly(sooner, later);
        PendingApprovalView first = views.get(0);
        assertThat(first.toolName()).isEqualTo("deploy_release");
        assertThat(first.riskLevel()).isEqualTo("HIGH");
        assertThat(first.requestedBy()).isEqualTo(REQUESTER_B);
        assertThat(first.requestedByName()).isEqualTo("Member 8963");
        assertThat(first.reason()).isEqualTo("较早失效");
        assertThat(first.expiresAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(first.version()).isZero();
    }

    /* c) 已决策状态（APPROVED / REJECTED / CANCELLED / EXPIRED）与其他公司的 PENDING 都不出现。 */
    @Test
    void decidedAndForeignApprovalsAreExcluded() {
        String pending = insertApproval(ORG, REQUESTER_A, "rotate_secret", "HIGH", "待处理", "PENDING",
                "2026-03-01 00:00:00.000000");
        insertApproval(ORG, REQUESTER_A, "rotate_secret", "HIGH", "已批准", "APPROVED", "2026-03-02 00:00:00.000000");
        insertApproval(ORG, REQUESTER_A, "rotate_secret", "HIGH", "已拒绝", "REJECTED", "2026-03-03 00:00:00.000000");
        insertApproval(ORG, REQUESTER_A, "rotate_secret", "HIGH", "已取消", "CANCELLED", "2026-03-04 00:00:00.000000");
        insertApproval(ORG, REQUESTER_A, "rotate_secret", "HIGH", "已过期", "EXPIRED", "2026-03-05 00:00:00.000000");
        /* 审批人在其他公司的审批不可见。 */
        insertApproval(OTHER_ORG, REQUESTER_A, "rotate_secret", "HIGH", "外公司待处理", "PENDING",
                "2026-02-01 00:00:00.000000");

        List<PendingApprovalView> views = approvalService.pending(APPROVER, ORG, "PENDING");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(pending);
    }

    /* d) 返回类型只含契约字段，不含任何冻结材料。 */
    @Test
    void pendingViewExposesOnlyContractFields() {
        Set<String> components = Arrays.stream(PendingApprovalView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(components).containsExactlyInAnyOrder("id", "toolName", "riskLevel", "requestedBy",
                "requestedByName", "reason", "expiresAt", "version");
        assertThat(components).doesNotContain("argumentHash", "frozenArgumentsEncrypted", "resourceVersions",
                "resourceVersionsJson", "toolVersion", "runId", "toolCallId", "approverUserId", "status", "type");
    }

    /* e) status 传非法值抛 IllegalArgumentException。 */
    @Test
    void unsupportedStatusIsRejected() {
        assertThatThrownBy(() -> approvalService.pending(APPROVER, ORG, "APPROVED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* 返回该测试夹具下固定的显示名，便于断言 requestedByName。 */
    private String displayName(long userId) {
        return "Member " + userId;
    }

    /* 插入有效成员关系与全局角色，供权限反查使用。 */
    private void insertMember(long userId, long organizationId, String roleCode) {
        jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) "
                + "VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", userId, organizationId, userId);
        jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) "
                + "SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=? AND organization_id IS NULL",
                userId, roleCode);
    }

    /* 造一条审批事实并返回其标识；每个审批使用独立 Run 满足唯一键与 FK。 */
    private String insertApproval(long organizationId, long requester, String toolName, String riskLevel,
            String reason, String status, String expiresAt) {
        String runId = nextId('0');
        jdbc.update("INSERT INTO agent_runs (id, organization_id, work_item_id, user_id, skill, "
                + "medium_tool_confirmation, message_redacted, client_request_id, request_hash, status, "
                + "prompt_version, last_sequence, version, created_at, updated_at) VALUES "
                + "(?, ?, NULL, ?, 'PRODUCT', 'ASK', ?, ?, SHA2('x', 256), 'RUNNING', 'fake-runner-v1', 0, 0, "
                + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                runId, organizationId, requester, "12 chars", "client-" + runId);
        String approvalId = nextId('1');
        jdbc.update("INSERT INTO approvals(id,organization_id,run_id,tool_call_id,type,risk_level,status,"
                + "requested_by,approver_user_id,tool_name,tool_version,argument_hash,"
                + "frozen_arguments_encrypted,resource_versions_json,reason,expires_at,decided_at,"
                + "version,created_at) VALUES(?,?,?,?,'TOOL_EXECUTION',?,?,?,NULL,?,1,'argument-hash',"
                + "'encrypted-arguments',CAST('[]' AS JSON),?,?,NULL,0,UTC_TIMESTAMP(6))",
                approvalId, organizationId, runId, "call-" + approvalId, riskLevel, status, requester, toolName,
                reason, expiresAt);
        return approvalId;
    }

    /* 生成 26 位的唯一标识，前缀区分 Run 与审批，满足 char(26) 列宽。 */
    private String nextId(char prefix) {
        sequence++;
        return prefix + String.format("%025d", sequence);
    }
}
