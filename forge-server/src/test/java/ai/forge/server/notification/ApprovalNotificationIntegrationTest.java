package ai.forge.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.agent.application.ApprovalService;
import ai.forge.server.agent.application.ApprovalSnapshot;
import ai.forge.server.agent.application.ToolContract;
import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentRunStatus;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.ApprovalStatus;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.release.application.DeploymentService;
import ai.forge.server.release.application.DeploymentView;
import ai.forge.server.release.application.ReleaseStore;
import ai.forge.server.release.application.ReleaseView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/* 验证审批类通知生产者：Agent HIGH Tool 审批与人工部署审批在真实事务内写入通知，且 MEDIUM/重放/过期不写入。 */
@Transactional
class ApprovalNotificationIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 本测试专用公司标识，与其他集成测试的夹具隔离。 */
    private static final long ORG = 8910L;
    /* 审批发起人（Run 发起用户），持 PRODUCT 角色，不持有 approval.decide。 */
    private static final long REQUESTER = 8911L;
    /* 持全局 ADMIN 角色的候选审批人，持有 approval.decide。 */
    private static final long ADMIN = 8912L;
    /* 持 DEVELOPER_ADMIN 角色的候选审批人，同样持有 approval.decide。 */
    private static final long DEV_ADMIN = 8913L;
    /* 持 QA 角色、不持有 approval.decide 的普通成员。 */
    private static final long QA_USER = 8914L;
    /* 持 DEVELOPER 角色的成员：持有 release.deploy 与 agent.run，但不持有 approval.decide。 */
    private static final long DEVELOPER = 8915L;

    /* 搭建身份与发布夹具事实，并断言通知落库结果。 */
    @Autowired private JdbcTemplate jdbc;
    /* 直接调用审批应用服务验证通知写入时机。 */
    @Autowired private ApprovalService approvalService;
    /* 直接调用部署应用服务验证通知写入时机。 */
    @Autowired private DeploymentService deploymentService;
    /* 搭建 Release 与 Precheck 事实。 */
    @Autowired private ReleaseStore releases;
    /* 构造结构化 Tool 参数。 */
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void seedOrganizationAndMembers() {
        for (long id = REQUESTER; id <= DEVELOPER; id++) {
            jdbc.update("INSERT INTO users(id,email,normalized_email,display_name,password_hash,status,"
                    + "created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                    id, "approval" + id + "@example.com", "approval" + id + "@example.com", "Member " + id,
                    "$2a$10$placeholder");
        }
        jdbc.update("INSERT INTO organizations(id,name,slug,owner_user_id,created_at,updated_at) "
                + "VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ORG, "Approval", "approval-org", REQUESTER);
        insertMember(REQUESTER, "PRODUCT");
        insertMember(ADMIN, "ADMIN");
        insertMember(DEV_ADMIN, "DEVELOPER_ADMIN");
        insertMember(QA_USER, "QA");
        insertMember(DEVELOPER, "DEVELOPER");
    }

    /* a) HIGH 审批请求只通知持有 approval.decide 的成员，并排除发起人。 */
    @Test
    void highRiskRequestNotifiesPermissionHoldersExceptRequester() {
        ApprovalSnapshot approval = approvalService.request(run(REQUESTER, insertRunningRun(REQUESTER)),
                contract("rotate_secret", "HIGH"), "call-high", objectMapper.createObjectNode());

        assertThat(approval.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(notificationRows(ADMIN)).hasSize(1);
        assertThat(notificationRows(DEV_ADMIN)).hasSize(1);
        Map<String, Object> row = notificationRows(ADMIN).get(0);
        assertThat(row.get("type")).isEqualTo("APPROVAL_REQUESTED");
        assertThat(row.get("resource_type")).isEqualTo("APPROVAL");
        assertThat(row.get("resource_id")).isEqualTo(approval.id());
        assertThat(row.get("title")).isEqualTo("待审批：rotate_secret");
        assertThat((String) row.get("body")).contains("15 分钟").contains("rotate_secret");
        /* 未持权限的成员与发起人本人均不收到通知。 */
        assertThat(notificationRows(REQUESTER)).isEmpty();
        assertThat(notificationRows(QA_USER)).isEmpty();
        assertThat(notificationRows(DEVELOPER)).isEmpty();
    }

    /* b) MEDIUM 审批请求由发起人会话内确认，不产生任何通知。 */
    @Test
    void mediumRiskRequestWritesNoNotification() {
        ApprovalSnapshot approval = approvalService.request(run(REQUESTER, insertRunningRun(REQUESTER)),
                contract("create_comment", "MEDIUM"), "call-medium", objectMapper.createObjectNode());

        assertThat(approval.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(countInOrg()).isZero();
    }

    /* c) 同一 (runId, toolCallId) 重放请求不再新增审批，也不新增通知。 */
    @Test
    void replayedRequestAddsNeitherApprovalNorNotification() {
        String runId = insertRunningRun(REQUESTER);
        ToolContract contract = contract("rotate_secret", "HIGH");
        JsonNode arguments = objectMapper.createObjectNode();
        ApprovalSnapshot first = approvalService.request(run(REQUESTER, runId), contract, "call-replay", arguments);
        long notificationsAfterFirst = countInOrg();

        ApprovalSnapshot replay = approvalService.request(run(REQUESTER, runId), contract, "call-replay", arguments);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approvals WHERE organization_id=?", Long.class, ORG))
                .isEqualTo(1);
        assertThat(countInOrg()).isEqualTo(notificationsAfterFirst);
    }

    /* d) 他人批准时通知发起人；决策者自己不在收件人之列。 */
    @Test
    void decisionNotifiesRequesterOnly() {
        String runId = insertRunningRun(REQUESTER);
        ApprovalSnapshot pending = approvalService.request(run(REQUESTER, runId),
                contract("rotate_secret", "HIGH"), "call-decide", objectMapper.createObjectNode());

        ApprovalSnapshot decided = approvalService.decide(ADMIN, ORG, pending.id(), true, pending.version(),
                "req-decide");

        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(notificationRows(REQUESTER)).hasSize(1);
        Map<String, Object> row = notificationRows(REQUESTER).get(0);
        assertThat(row.get("type")).isEqualTo("APPROVAL_DECIDED");
        assertThat(row.get("resource_type")).isEqualTo("AGENT_RUN");
        assertThat(row.get("resource_id")).isEqualTo(runId);
        assertThat(row.get("title")).isEqualTo("审批通过：rotate_secret");
        assertThat(countFor(ADMIN, "APPROVAL_DECIDED")).isZero();
    }

    /* d) 他人驳回时同样通知发起人，正文说明 Agent 已终止。 */
    @Test
    void rejectionNotifiesRequester() {
        String runId = insertRunningRun(REQUESTER);
        ApprovalSnapshot pending = approvalService.request(run(REQUESTER, runId),
                contract("rotate_secret", "HIGH"), "call-reject", objectMapper.createObjectNode());

        ApprovalSnapshot decided = approvalService.decide(ADMIN, ORG, pending.id(), false, pending.version(),
                "req-reject");

        assertThat(decided.status()).isEqualTo(ApprovalStatus.REJECTED);
        Map<String, Object> row = notificationRows(REQUESTER).get(0);
        assertThat(row.get("type")).isEqualTo("APPROVAL_DECIDED");
        assertThat(row.get("title")).isEqualTo("审批驳回：rotate_secret");
        assertThat((String) row.get("body")).contains("终止");
    }

    /* e) MEDIUM 由发起人自确认时，收件人等于操作者，notify 排除后不写入通知。 */
    @Test
    void mediumSelfConfirmationWritesNoNotification() {
        String runId = insertRunningRun(DEVELOPER);
        ApprovalSnapshot pending = approvalService.request(run(DEVELOPER, runId),
                contract("create_comment", "MEDIUM"), "call-self", objectMapper.createObjectNode());

        ApprovalSnapshot decided = approvalService.decide(DEVELOPER, ORG, pending.id(), true, pending.version(),
                "req-self");

        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(countInOrg()).isZero();
    }

    /* f) 人工部署申请通知审批人，决策后通知申请人，资源指向 Release 关联需求。 */
    @Test
    void humanDeploymentRequestAndDecisionNotifyApproversAndRequester() {
        ReleaseView release = createDeployableRelease();

        DeploymentView deployment = deploymentService.request(DEVELOPER, ORG, release.id(), false,
                "idem-human", "req-human");

        assertThat(countFor(ADMIN, "DEPLOYMENT_APPROVAL_REQUESTED")).isEqualTo(1);
        assertThat(countFor(DEV_ADMIN, "DEPLOYMENT_APPROVAL_REQUESTED")).isEqualTo(1);
        Map<String, Object> requested = onlyRow(ADMIN, "DEPLOYMENT_APPROVAL_REQUESTED");
        assertThat(requested.get("resource_type")).isEqualTo("REQUIREMENT_RELEASE");
        assertThat(requested.get("resource_id")).isEqualTo(Long.toString(release.itemIds().get(0)));
        assertThat(requested.get("title")).isEqualTo("待审批：发布部署 v1.0.0");
        /* 申请人自己不收到待审批通知。 */
        assertThat(countFor(DEVELOPER, "DEPLOYMENT_APPROVAL_REQUESTED")).isZero();

        DeploymentView decided = deploymentService.decide(ADMIN, ORG, deployment.id(), true, deployment.version(),
                "req-decide");

        assertThat(decided.status()).isEqualTo("APPROVED");
        assertThat(countFor(DEVELOPER, "DEPLOYMENT_APPROVAL_DECIDED")).isEqualTo(1);
        Map<String, Object> result = onlyRow(DEVELOPER, "DEPLOYMENT_APPROVAL_DECIDED");
        assertThat(result.get("resource_type")).isEqualTo("REQUIREMENT_RELEASE");
        assertThat(result.get("resource_id")).isEqualTo(Long.toString(release.itemIds().get(0)));
        assertThat(result.get("title")).isEqualTo("部署审批通过：v1.0.0");
        assertThat(countFor(ADMIN, "DEPLOYMENT_APPROVAL_DECIDED")).isZero();
    }

    /* f) AGENT_TOOL 路径（已由 Agent HIGH 审批覆盖、创建即批准）不写入部署待审批通知。 */
    @Test
    void agentToolDeploymentWritesNoRequestNotification() {
        ReleaseView release = createDeployableRelease();
        String runId = insertRunningRun(REQUESTER);
        ApprovalSnapshot agentApproval = approvalService.request(run(REQUESTER, runId),
                contract("rotate_secret", "HIGH"), "call-agent-approval", objectMapper.createObjectNode());

        DeploymentView deployment = deploymentService.requestFromApprovedAgent(DEVELOPER, ORG, release.id(), false,
                "idem-agent", agentApproval.id(), runId);

        assertThat(deployment.status()).isEqualTo("APPROVED");
        assertThat(countTypeInOrg("DEPLOYMENT_APPROVAL_REQUESTED")).isZero();
    }

    /*
     * 直接搭建一条 PASS 且版本对齐的 Release 事实：单一需求、无 MR/Pipeline、无 Test Run、无组织策略。
     * 使用 JdbcTemplate 造数并让首次 MyBatis 读取发生在插入之后，避免同事务内 MyBatis 一级缓存返回旧快照。
     */
    private ReleaseView createDeployableRelease() {
        jdbc.update("INSERT INTO work_items(organization_id,item_number,item_key,type,title,description,status,"
                + "priority,reporter_user_id,created_at,updated_at,version) VALUES(?,1,'REL-DEPLOY','REQUIREMENT',"
                + "'Release scope','','READY_FOR_RELEASE','MEDIUM',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                ORG, REQUESTER);
        long itemId = jdbc.queryForObject(
                "SELECT id FROM work_items WHERE organization_id=? AND item_key='REL-DEPLOY'", Long.class, ORG);
        jdbc.update("INSERT INTO releases(organization_id,version_name,environment,status,policy_snapshot_json,"
                + "created_by,created_at,updated_at,version) VALUES(?,?,'production','DRAFT',CAST(? AS JSON),?,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)", ORG, "v1.0.0", "{\"approvalTtlMinutes\":60}", DEVELOPER);
        long releaseId = jdbc.queryForObject("SELECT id FROM releases WHERE organization_id=? AND version_name=? "
                + "AND environment='production'", Long.class, ORG, "v1.0.0");
        jdbc.update("INSERT INTO release_items(release_id,work_item_id,organization_id) VALUES(?,?,?)",
                releaseId, itemId, ORG);
        /* 版本指纹与 loadFacts 重组结果一致：需求号键沿用 item_key，缺失 MR 与 Test Run 记为零版本。 */
        String versionsJson = "{\"release\":0,\"policy\":0,\"item:REL-DEPLOY\":0,"
                + "\"pipeline:REL-DEPLOY:MISSING_MR\":0,\"testRun:REL-DEPLOY\":0}";
        jdbc.update("INSERT INTO release_prechecks(organization_id,release_id,status,checks_json,checked_at,"
                + "checked_by_type,checked_by_id,resource_versions_json) VALUES(?,?,'PASS',JSON_ARRAY(),"
                + "UTC_TIMESTAMP(6),'USER',?,CAST(? AS JSON))", ORG, releaseId, ADMIN, versionsJson);
        return releases.find(ORG, releaseId).orElseThrow();
    }

    /* 插入一条 RUNNING 状态的 Run 事实，绕过 Dispatcher 避免状态竞态。 */
    private String insertRunningRun(long userId) {
        String runId = ("00000000000000000000000000" + System.nanoTime());
        runId = runId.substring(runId.length() - 26);
        jdbc.update("INSERT INTO agent_runs (id, organization_id, work_item_id, user_id, skill, "
                + "medium_tool_confirmation, message_redacted, client_request_id, request_hash, status, "
                + "prompt_version, last_sequence, version, created_at, updated_at) VALUES "
                + "(?, ?, NULL, ?, 'PRODUCT', 'ASK', ?, ?, SHA2('x', 256), 'RUNNING', 'fake-runner-v1', 0, 0, "
                + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                runId, ORG, userId, "12 chars", "client-" + runId);
        return runId;
    }

    /* 构造用于 request 的 Run 快照；状态字段只参与审批事实写入，不参与通知判定。 */
    private AgentRun run(long userId, String runId) {
        return new AgentRun(runId, ORG, null, userId, AgentSkill.PRODUCT, MediumToolConfirmation.ASK,
                AgentRunStatus.RUNNING, 0, null, null, null, Instant.now());
    }

    /* 构造测试用 Tool 契约；名称避开需要冻结资源版本的专有 Tool。 */
    private ToolContract contract(String name, String riskLevel) {
        return new ToolContract(name, 1, "test contract", "requirement.read", riskLevel, false, false, Map.of(),
                "POST", "/internal/v1/tools/" + name + ":execute");
    }

    private void insertMember(long userId, String roleCode) {
        jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,status,created_at,updated_at) "
                + "VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", userId, ORG, userId);
        jdbc.update("INSERT INTO member_roles(organization_member_id,role_id,created_at) "
                + "SELECT ?,id,UTC_TIMESTAMP(6) FROM roles WHERE code=? AND organization_id IS NULL",
                userId, roleCode);
    }

    private List<Map<String, Object>> notificationRows(long userId) {
        return jdbc.queryForList("SELECT * FROM notifications WHERE organization_id=? AND user_id=? ORDER BY id",
                ORG, userId);
    }

    private Map<String, Object> onlyRow(long userId, String type) {
        return jdbc.queryForMap("SELECT * FROM notifications WHERE organization_id=? AND user_id=? AND type=?",
                ORG, userId, type);
    }

    private long countFor(long userId, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE organization_id=? AND user_id=? AND type=?",
                Long.class, ORG, userId, type);
    }

    private long countTypeInOrg(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE organization_id=? AND type=?",
                Long.class, ORG, type);
    }

    private long countInOrg() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE organization_id=?", Long.class, ORG);
    }
}
