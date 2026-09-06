package ai.forge.server.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.gitlab.application.DevelopmentStore;
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

    @Autowired
    private DevelopmentStore developmentStore;

    @TempDir
    private Path temporaryMigrationDirectory;

    @Test
    void migratesEmptyMySqlWithExpectedBaselineAndSingletonSettings() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("24");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM instance_settings", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM instance_settings", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT table_collation FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = 'instance_settings'",
                        String.class))
                .isEqualTo("utf8mb4_0900_ai_ci");
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
                .containsExactly("release.manage", "release.precheck", "release.read");
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
    void identityWorkspaceBootstrapSchemaHasRequiredTablesAndComments() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name IN "
                                + "('users','organizations','workspaces','workspace_members','roles','member_roles','audit_logs','projects','project_members')",
                        String.class))
                .containsExactlyInAnyOrder(
                        "users", "organizations", "workspaces", "workspace_members", "roles", "member_roles", "audit_logs",
                        "projects", "project_members");

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
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code FROM permissions ORDER BY code", String.class))
                .contains(
                        "member.manage", "member.read", "project.manage", "project.read",
                        "bug.create", "bug.edit", "bug.read", "bug.resolve", "bug.verify",
                        "qa.execute", "qa.manage", "qa.read",
                        "requirement.create", "requirement.edit", "requirement.read",
                        "task.create", "task.edit", "task.read",
                        "ux.create", "ux.edit", "ux.read",
                        "workspace.manage", "workspace.read");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id WHERE r.code = 'OWNER'",
                Integer.class))
                .isEqualTo(39);
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
        assertThat(developmentStore.findPendingOperation()).isEmpty();
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
                .containsExactly("ADMIN", "DEVELOPER", "OWNER");
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
                .containsExactly("ADMIN", "OWNER", "PRODUCT", "UX");
    }

    @Test
    void workItemMigrationCreatesScopedSequenceAndAggregateTables() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name IN "
                                + "('project_item_sequences','work_items')",
                        String.class))
                .containsExactlyInAnyOrder("project_item_sequences", "work_items");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT index_name FROM information_schema.statistics "
                                + "WHERE table_schema = DATABASE() AND table_name = 'work_items'",
                        String.class))
                .contains(
                        "uq_work_items_project_number",
                        "uq_work_items_project_key",
                        "idx_work_items_project_type_status",
                        "idx_work_items_workspace_assignee_status");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('project_item_sequences','work_items') AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('project_item_sequences','work_items') AND table_comment = ''",
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
                                + "AND table_name IN ('project_policies','work_item_labels',"
                                + "'work_item_relations','comments')",
                        String.class))
                .containsExactlyInAnyOrder(
                        "project_policies", "work_item_labels", "work_item_relations", "comments");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('project_policies','work_item_labels',"
                                + "'work_item_relations','comments') AND column_comment = ''",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                                + "AND table_name IN ('project_policies','work_item_labels',"
                                + "'work_item_relations','comments') AND table_comment = ''",
                        Integer.class))
                .isZero();
    }

    @Test
    void developmentQaMigrationAddsDocumentedDefaultSafePolicyAndPermission() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CONCAT(column_default, ':', column_comment) FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'project_policies' "
                                + "AND column_name = 'ci_required'",
                        String.class))
                .isEqualTo("1:提交 QA 前是否强制每个 Dev Task 的 MR 当前 head Pipeline 成功；无仓库时不放行");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id=rp.role_id "
                                + "JOIN permissions p ON p.id=rp.permission_id "
                                + "WHERE p.code='development.submit' ORDER BY r.code",
                        String.class))
                .containsExactly("ADMIN", "DEVELOPER", "OWNER");
    }

    @Test
    void authenticationAuditAllowsInstanceScopedAnonymousEvents() {
        Map<String, String> nullability = jdbcTemplate.query(
                        "SELECT column_name, is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'audit_logs' "
                                + "AND column_name IN ('workspace_id', 'actor_id')",
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("column_name"), resultSet.getString("is_nullable")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(nullability).containsExactlyInAnyOrderEntriesOf(Map.of(
                "workspace_id", "YES",
                "actor_id", "YES"));
    }

    @Test
    void repeatedMigrationDoesNotChangeSchema() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void changedPublishedMigrationFailsChecksumValidation() throws IOException {
        copyMigration("V1__baseline.sql");
        copyMigration("V2__instance_settings.sql");
        copyMigration("V3__identity_workspace_bootstrap.sql");
        copyMigration("V4__authentication_audit_scope.sql");
        copyMigration("V5__project_member_scope.sql");
        copyMigration("V6__rbac_permissions.sql");
        copyMigration("V7__work_item_aggregate.sql");
        copyMigration("V8__requirement_workflow.sql");
        Files.writeString(
                temporaryMigrationDirectory.resolve("V1__baseline.sql"),
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
