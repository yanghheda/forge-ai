package ai.forge.server.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.platform.health.QdrantHealthIndicator;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class InfrastructureHealthIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void readinessRequiresMySqlAndRedisWhileQdrantIsHealthy() {
        ResponseEntity<String> readiness = restTemplate.getForEntity("/actuator/health/readiness", String.class);
        ResponseEntity<String> qdrant = restTemplate.getForEntity("/actuator/health/qdrant", String.class);

        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).contains("\"status\":\"UP\"");
        assertThat(qdrant.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(qdrant.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void unavailableQdrantReportsDegradedWithoutPretendingItIsFactStorage() {
        QdrantHealthIndicator indicator = new QdrantHealthIndicator(
                "http://127.0.0.1:1", Duration.ofMillis(100));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("role", "rebuildable-derived-index");
    }
}
