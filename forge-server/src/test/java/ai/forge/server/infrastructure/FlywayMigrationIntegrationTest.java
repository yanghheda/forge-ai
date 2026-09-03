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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM instance_settings", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM instance_settings", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT table_collation FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = 'instance_settings'",
                        String.class))
                .isEqualTo("utf8mb4_0900_ai_ci");
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
                                + "('users','organizations','workspaces','workspace_members','roles','member_roles','audit_logs')",
                        String.class))
                .containsExactlyInAnyOrder(
                        "users", "organizations", "workspaces", "workspace_members", "roles", "member_roles", "audit_logs");

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
    void repeatedMigrationDoesNotChangeSchema() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void changedPublishedMigrationFailsChecksumValidation() throws IOException {
        copyMigration("V1__baseline.sql");
        copyMigration("V2__instance_settings.sql");
        copyMigration("V3__identity_workspace_bootstrap.sql");
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
