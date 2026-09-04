package ai.forge.server.infrastructure;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class InfrastructureIntegrationTestBase {

    /* 集成测试使用的真实 MySQL 8 容器，验证 MySQL 方言与迁移行为。 */
    @Container
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName("forge_ai")
            .withUsername("forge_ai")
            .withPassword("test-only-password");

    /* 集成测试使用的真实 Redis 容器，避免以内存替身掩盖连接问题。 */
    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
            .withExposedPorts(6379);

    /* 集成测试使用的真实 Qdrant 容器，用于验证就绪与降级探测。 */
    @Container
    protected static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.15.3"))
            .withExposedPorts(6333);

    @DynamicPropertySource
    static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.hikari.connection-timeout", () -> 1000L);
        registry.add("forge.web.allowed-origins", () -> "http://localhost");
        registry.add("forge.infrastructure.agent.health-enabled", () -> false);
        registry.add("forge.infrastructure.agent.dispatch-enabled", () -> false);
        registry.add(
                "forge.infrastructure.agent.jwt-secret",
                () -> "test-only-internal-jwt-secret-32-bytes-minimum");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("forge.infrastructure.qdrant.base-url",
                () -> "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333));
    }
}
