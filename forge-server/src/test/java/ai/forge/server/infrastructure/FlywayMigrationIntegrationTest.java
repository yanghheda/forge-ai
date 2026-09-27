package ai.forge.server.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private Path temporaryMigrationDirectory;

    @Test
    void migratesEmptyMySqlWithExpectedBaselineAndSingletonSettings() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM instance_settings", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM instance_settings", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT table_collation FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = 'instance_settings'",
                        String.class))
                .isEqualTo("utf8mb4_0900_ai_ci");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT column_comment FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'test_results' "
                                + "AND column_name = 'status'",
                        String.class))
                .contains("IN_PROGRESS");
    }

    @Test
    void releaseMigrationCreatesCommentedImmutableSnapshotFactsAndPermissions() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('releases','release_items','release_prechecks')",
                        String.class))
                .containsExactlyInAnyOrder("releases", "release_items", "release_prechecks");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('releases','release_items','release_prechecks') "
                                + "AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code FROM permissions WHERE code LIKE 'release.%' ORDER BY code", String.class))
                .containsExactly("release.deploy", "release.manage", "release.precheck", "release.read");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT p.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.code = 'DEVELOPER_ADMIN' AND p.code IN ('approval.decide','release.read') "
                                + "ORDER BY p.code",
                        String.class))
                .containsExactly("approval.decide", "release.read");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE p.code = 'approval.decide' ORDER BY r.code",
                        String.class))
                .containsExactly("ADMIN", "DEVELOPER_ADMIN", "OWNER");
    }

    @Test
    void migrationDocumentsTableAndEveryColumnInMySqlMetadata() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT table_comment FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = 'instance_settings'",
                        String.class))
                .isEqualTo("实例级配置与初始化状态的唯一事实记录");

        Map<String, String> comments = jdbcTemplate.query(
                        "SELECT column_name, column_comment FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'instance_settings'",
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("column_name"), resultSet.getString("column_comment")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(comments).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "固定为 1 的单例主键",
                "initialized_at", "首次管理员初始化完成时间；为空表示实例尚未初始化",
                "default_organization_id", "初始化后创建的默认组织标识；为空表示实例尚未完成初始化",
                "settings_json", "不属于独立领域表的实例级扩展设置",
                "version", "实例设置并发更新使用的乐观锁版本"));
    }

    @Test
    void companyIdentitySchemaHasRequiredTablesAndNoLegacyScopeTables() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name IN "
                                + "('users','organizations','organization_members','roles','member_roles','audit_logs')",
                        String.class))
                .containsExactlyInAnyOrder(
                        "users", "organizations", "organization_members", "roles", "member_roles", "audit_logs");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('workspaces','workspace_members','projects','project_members','project_policies','project_item_sequences')",
                        Integer.class))
                .isZero();

        Integer undocumentedTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' AND table_comment = ''",
                Integer.class);
        Integer undocumentedColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' AND column_comment = ''",
                Integer.class);
        assertThat(undocumentedTables).isZero();
        assertThat(undocumentedColumns).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT data_type FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'instance_settings' "
                                + "AND column_name = 'default_organization_id'",
                        String.class))
                .isEqualTo("bigint");
    }

    @Test
    void rbacMigrationSeedsDefaultRolesAndCorePermissions() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles WHERE system_role = TRUE", Integer.class))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code FROM permissions ORDER BY code", String.class))
                .contains(
                        "member.manage", "member.read",
                        "bug.create", "bug.edit", "bug.read", "bug.resolve", "bug.verify",
                        "qa.execute", "qa.manage", "qa.read",
                        "requirement.create", "requirement.edit", "requirement.read",
                        "task.create", "task.edit", "task.read",
                        "ux.create", "ux.edit", "ux.read");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id WHERE r.code = 'OWNER'",
                Integer.class))
                .isEqualTo(17);
    }

    @Test
    void domainAdminMigrationReplacesReleaseApproverWithDomainAdmins() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code FROM roles WHERE organization_id IS NULL AND system_role = TRUE ORDER BY code",
                        String.class))
                .containsExactly(
                        "ADMIN", "DEVELOPER", "DEVELOPER_ADMIN", "OWNER", "PRODUCT", "PRODUCT_ADMIN",
                        "QA", "QA_ADMIN", "UX", "UX_ADMIN");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM roles WHERE code = 'ADMIN' AND organization_id IS NULL", String.class))
                .isEqualTo("全局管理员");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM roles r WHERE r.code IN "
                                + "('PRODUCT_ADMIN','UX_ADMIN','DEVELOPER_ADMIN','QA_ADMIN') "
                                + "AND r.system_role = TRUE AND r.organization_id IS NULL "
                                + "AND EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id)",
                        Integer.class))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code FROM permissions WHERE code IN ('requirement.approve','ux.approve') ORDER BY code",
                        String.class))
                .containsExactly("requirement.approve", "ux.approve");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE p.code = 'requirement.approve' ORDER BY r.code",
                        String.class))
                .containsExactly("PRODUCT_ADMIN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE p.code = 'ux.approve' ORDER BY r.code",
                        String.class))
                .containsExactly("UX_ADMIN");
    }

    /* 验收权限码与审核码分离：产品基础角色与产品管理员可验收，审核码归属不受影响（ADR-024）。 */
    @Test
    void acceptancePermissionIsGrantedToProductDomainMembers() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE p.code = 'requirement.accept' ORDER BY r.code",
                        String.class))
                .containsExactly("PRODUCT", "PRODUCT_ADMIN");
    }

    /* 发布写权限归属研发领域：开发与开发管理员可创建、预检与申请模拟部署，其余角色只保留读取（ADR-025、ADR-026）。 */
    @Test
    void releaseWritePermissionsBelongToDeveloperDomain() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL "
                                + "AND p.code IN ('release.manage','release.precheck','release.deploy') "
                                + "GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 3 ORDER BY r.code",
                        String.class))
                .containsExactly("DEVELOPER", "DEVELOPER_ADMIN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND p.code LIKE 'release.%' "
                                + "GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 1 ORDER BY r.code",
                        String.class))
                .containsExactly("ADMIN", "OWNER", "PRODUCT", "PRODUCT_ADMIN", "QA", "QA_ADMIN",
                        "UX", "UX_ADMIN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND p.code = 'release.read' ORDER BY r.code",
                        String.class))
                .containsExactly("ADMIN", "DEVELOPER", "DEVELOPER_ADMIN", "OWNER", "PRODUCT", "PRODUCT_ADMIN",
                        "QA", "QA_ADMIN", "UX", "UX_ADMIN");
    }

    /* 五个模块所有系统角色都可读，写权限只保留给该模块的基础角色与领域管理员，Owner/Admin 不持有任何模块写权限（ADR-026）。 */
    @Test
    void moduleWritePermissionsBelongToOwningDomainOnly() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND p.code IN "
                                + "('requirement.edit','document.create','document.edit','document.publish') "
                                + "GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 4 ORDER BY r.code",
                        String.class))
                .containsExactly("PRODUCT", "PRODUCT_ADMIN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND p.code IN "
                                + "('ux.create','ux.edit','ux.document.create','ux.document.edit','ux.document.publish') "
                                + "GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 5 ORDER BY r.code",
                        String.class))
                .containsExactly("UX", "UX_ADMIN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND p.code IN "
                                + "('task.create','task.edit','development.submit','repo.write',"
                                + "'development.document.create','development.document.edit',"
                                + "'development.document.publish') "
                                + "GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 7 ORDER BY r.code",
                        String.class))
                .containsExactly("DEVELOPER", "DEVELOPER_ADMIN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND p.code IN "
                                + "('qa.create','qa.edit','qa.manage','qa.execute','bug.create','bug.edit','bug.verify') "
                                + "GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 7 ORDER BY r.code",
                        String.class))
                .containsExactly("QA", "QA_ADMIN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND p.code = 'bug.resolve' ORDER BY r.code",
                        String.class))
                .containsExactly("DEVELOPER", "DEVELOPER_ADMIN");
    }

    /* 五个模块的读取权限对所有系统角色开放；Owner 与全局管理员保留需求创建这一全局入口。 */
    @Test
    void moduleReadPermissionsAndGlobalRequirementCreationAreOpen() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND r.system_role = TRUE "
                                + "AND p.code IN ('qa.read','bug.read','repo.read','release.read') "
                                + "GROUP BY r.code HAVING COUNT(DISTINCT p.code) = 4 ORDER BY r.code",
                        String.class))
                .containsExactly("ADMIN", "DEVELOPER", "DEVELOPER_ADMIN", "OWNER", "PRODUCT", "PRODUCT_ADMIN",
                        "QA", "QA_ADMIN", "UX", "UX_ADMIN");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.organization_id IS NULL AND p.code = 'requirement.create' ORDER BY r.code",
                        String.class))
                .containsExactly("ADMIN", "OWNER", "PRODUCT", "PRODUCT_ADMIN");
    }

    @Test
    void gitLabMigrationCreatesCommentedScopedFactsAndPermissions() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('secrets','gitlab_connections','git_repositories')",
                        String.class))
                .containsExactlyInAnyOrder("secrets", "gitlab_connections", "git_repositories");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('secrets','gitlab_connections','git_repositories') "
                                + "AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code FROM permissions WHERE code IN ('integration.manage','repo.read') ORDER BY code",
                        String.class))
                .containsExactly("integration.manage", "repo.read");
    }

    @Test
    void developmentMigrationCreatesCommentedBranchMergeRequestAndOperationFacts() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('branches','merge_requests','source_control_operations')",
                        String.class))
                .containsExactlyInAnyOrder("branches", "merge_requests", "source_control_operations");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('branches','merge_requests','source_control_operations') "
                                + "AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name='source_control_operations' AND column_name IN "
                                + "('target_branch','attempt_count','next_attempt_at','last_error_code')",
                        String.class))
                .containsExactlyInAnyOrder(
                        "target_branch", "attempt_count", "next_attempt_at", "last_error_code");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM source_control_operations", Integer.class)).isZero();
    }

    @Test
    void pipelineWebhookMigrationCreatesCommentedDeliveryAndSnapshotFacts() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('pipeline_runs','webhook_deliveries')",
                        String.class))
                .containsExactlyInAnyOrder("pipeline_runs", "webhook_deliveries");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('pipeline_runs','webhook_deliveries') AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('pipeline_runs','webhook_deliveries') AND table_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id=rp.role_id "
                                + "JOIN permissions p ON p.id=rp.permission_id WHERE p.code='repo.write' ORDER BY r.code",
                        String.class))
                .containsExactly("DEVELOPER", "DEVELOPER_ADMIN");
    }

    @Test
    void agentRunMigrationCreatesCommentedReplayFactsAndPermission() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('agent_runs','agent_steps','agent_events')",
                        String.class))
                .containsExactlyInAnyOrder("agent_runs", "agent_steps", "agent_events");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('agent_runs','agent_steps','agent_events') AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('agent_runs','agent_steps','agent_events') AND table_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id WHERE p.code = 'agent.run' ORDER BY r.code",
                        String.class))
                .containsExactly("ADMIN", "DEVELOPER", "DEVELOPER_ADMIN", "OWNER", "PRODUCT", "PRODUCT_ADMIN",
                        "QA", "QA_ADMIN", "UX", "UX_ADMIN");
    }

    @Test
    void workItemMigrationCreatesCompanySequenceAndAggregateTables() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name IN "
                                + "('organization_item_sequences','work_items')",
                        String.class))
                .containsExactlyInAnyOrder("organization_item_sequences", "work_items");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT index_name FROM information_schema.statistics "
                                + "WHERE table_schema = DATABASE() AND table_name = 'work_items'",
                        String.class))
                .contains(
                        "uq_work_items_organization_number",
                        "uq_work_items_organization_key",
                        "idx_work_items_organization_type_status",
                        "idx_work_items_organization_assignee_status");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('organization_item_sequences','work_items') AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('organization_item_sequences','work_items') AND table_comment = ''",
                        Integer.class))
                .isZero();
    }

    @Test
    void requirementWorkflowMigrationCreatesGuardReviewAndEventFacts() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name IN "
                                + "('requirement_details','review_records','work_item_events')",
                        String.class))
                .containsExactlyInAnyOrder("requirement_details", "review_records", "work_item_events");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT index_name FROM information_schema.statistics "
                                + "WHERE table_schema = DATABASE() AND table_name = 'work_item_events'",
                        String.class))
                .contains("idx_work_item_events_timeline", "uq_work_item_events_idempotency");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('requirement_details','review_records','work_item_events') "
                                + "AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('requirement_details','review_records','work_item_events') "
                                + "AND table_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT p.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.code IN ('OWNER','ADMIN','PRODUCT') AND p.code = 'requirement.review'",
                        String.class))
                .hasSize(3);
    }

    @Test
    void uxSkipRelationAndActivityMigrationCreatesScopedCommentedFacts() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('organization_policies','work_item_labels',"
                                + "'work_item_relations','comments')",
                        String.class))
                .containsExactlyInAnyOrder(
                        "organization_policies", "work_item_labels", "work_item_relations", "comments");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('organization_policies','work_item_labels',"
                                + "'work_item_relations','comments') AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('organization_policies','work_item_labels',"
                                + "'work_item_relations','comments') AND table_comment = ''",
                        Integer.class))
                .isZero();
    }

    @Test
    void developmentQaMigrationAddsDocumentedDefaultSafePolicyAndPermission() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CONCAT(column_default, ':', column_comment) FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'organization_policies' "
                                + "AND column_name = 'ci_required'",
                        String.class))
                .isEqualTo("1:提交 QA 前是否强制每个 Dev Task 的 MR 当前 head Pipeline 成功；无仓库时不放行");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id=rp.role_id "
                                + "JOIN permissions p ON p.id=rp.permission_id "
                                + "WHERE p.code='development.submit' ORDER BY r.code",
                        String.class))
                .containsExactly("DEVELOPER", "DEVELOPER_ADMIN");
    }

    @Test
    void authenticationAuditAllowsInstanceScopedAnonymousEvents() {
        Map<String, String> nullability = jdbcTemplate.query(
                        "SELECT column_name, is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'audit_logs' "
                                + "AND column_name IN ('organization_id', 'actor_id')",
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("column_name"), resultSet.getString("is_nullable")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(nullability).containsExactlyInAnyOrderEntriesOf(Map.of(
                "organization_id", "YES",
                "actor_id", "YES"));
    }

    @Test
    void repeatedMigrationDoesNotChangeSchema() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void changedPublishedMigrationFailsChecksumValidation() throws IOException {
        copyMigration("V1__company_platform_baseline.sql");
        Files.writeString(
                temporaryMigrationDirectory.resolve("V1__company_platform_baseline.sql"),
                System.lineSeparator() + "-- 模拟错误修改已发布迁移。",
                java.nio.file.StandardOpenOption.APPEND);

        Flyway changedFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + temporaryMigrationDirectory)
                .load();

        assertThatThrownBy(changedFlyway::validate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("checksum");
    }

    private void copyMigration(String migrationName) throws IOException {
        Path source = Path.of("src/main/resources/db/migration", migrationName);
        Files.copy(source, temporaryMigrationDirectory.resolve(migrationName));
    }
}
