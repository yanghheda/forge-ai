package ai.forge.server.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RedisReadinessFailureIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void unavailableRedisMakesReadinessFailClosed() {
        REDIS.stop();
        try {
            ResponseEntity<String> readiness = restTemplate.getForEntity("/actuator/health/readiness", String.class);

            assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(readiness.getBody()).contains("\"status\":\"DOWN\"");
        } finally {
            /* 基类容器由后续集成测试共用，验证故障后必须恢复。 */
            REDIS.start();
        }
    }
}
