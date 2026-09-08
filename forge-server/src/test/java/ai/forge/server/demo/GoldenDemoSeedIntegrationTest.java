package ai.forge.server.demo;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class GoldenDemoSeedIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 测试只用 JDBC 装载 Fixture 和断言数据库事实，不替代生产 Mapper。 */
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedIsRepeatableAndContainsTheCompleteGoldenDeliveryChain() throws Exception {
        loadFixture();
        loadFixture();

        assertThat(count("users", "id IN (32001, 32002)")).isEqualTo(2);
        assertThat(count("workspaces", "id = 32004")).isOne();
        assertThat(count("projects", "id = 32007")).isOne();
        assertThat(count("work_items", "workspace_id = 32004")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT title FROM work_items WHERE id = 32011", String.class))
                .isEqualTo("增加手机号验证码登录");
        assertThat(count("documents", "workspace_id = 32004 AND status = 'PUBLISHED'")).isEqualTo(3);
        assertThat(count("merge_requests", "id = 32044 AND state = 'merged'")).isOne();
        assertThat(count("pipeline_runs", "id = 32045 AND status = 'success'")).isOne();
        assertThat(count("test_runs", "id = 32051 AND status = 'COMPLETED'")).isOne();
        assertThat(count("work_items", "id = 32015 AND status = 'CLOSED'")).isOne();
        assertThat(count("release_prechecks", "id = 32061 AND status = 'PASS'")).isOne();
        assertThat(count("deployments", "id = 32062 AND mode = 'SIMULATED' AND status = 'SUCCEEDED'")).isOne();
        assertThat(count("agent_runs", "id = 'DEMA0000000000000000000001' AND status = 'SUCCEEDED'")).isOne();
        assertThat(count("agent_events", "run_id = 'DEMA0000000000000000000001'")).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(settings_json, '$.goldenDemoVersion')) "
                        + "FROM instance_settings WHERE id = 1",
                String.class)).isEqualTo("v1");
    }

    private void loadFixture() throws Exception {
        Path fixture = Path.of("..", "deploy", "demo", "golden-demo.sql").toAbsolutePath().normalize();
        try (var connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(fixture));
        }
    }

    private long count(String table, String predicate) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Long.class);
    }
}
